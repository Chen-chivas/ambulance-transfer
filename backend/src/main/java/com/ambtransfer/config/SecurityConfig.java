package com.ambtransfer.config;

import com.ambtransfer.dao.db.staff.StaffMapper;
import com.ambtransfer.dto.response.ApiError;
import com.ambtransfer.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    public static final String ISSUER = "ambulance-transfer";

    @Bean
    SecretKeySpec jwtKey(@Value("${app.auth.jwt-secret:}") String configured) {
        byte[] bytes;
        if (configured.isBlank()) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            LoggerFactory.getLogger(SecurityConfig.class).warn("JWT_SECRET 未配置，使用临时密钥；服务重启后需要重新登录。");
        } else {
            bytes = configured.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET 至少需要 32 字节");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKeySpec key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKeySpec key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, StaffMapper staff, ObjectMapper json) throws Exception {
        http.csrf(csrf -> csrf.disable()).cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/health", "/api/health/db").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/mock/**").hasRole("ADMIN")
                .requestMatchers("/api/company/**").hasRole("DISPATCHER")
                .requestMatchers("/api/driver/**").hasRole("DRIVER")
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((req, res, ex) -> writeError(res, json, 401, "UNAUTHORIZED", "请登录或重新登录"))
                .accessDeniedHandler((req, res, ex) -> writeError(res, json, 403, "FORBIDDEN", "没有此操作的权限")))
            .oauth2ResourceServer(oauth -> oauth
                .authenticationEntryPoint((req, res, ex) -> {
                    if (ex instanceof AuthenticationServiceException) writeError(res, json, 503, "DATABASE_UNAVAILABLE", "身份校验服务暂不可用，请稍后重试");
                    else writeError(res, json, 401, "UNAUTHORIZED", "登录已失效，请重新登录");
                })
                .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    long id;
                    try { id = Long.parseLong(token.getSubject()); }
                    catch (RuntimeException ex) { throw new OAuth2AuthenticationException("invalid_token"); }
                    com.ambtransfer.entity.Staff account;
                    try { account = staff.findById(id); }
                    catch (org.springframework.dao.DataAccessException ex) { throw new AuthenticationServiceException("身份校验服务暂不可用", ex); }
                    if (account == null || !account.enabled) throw new OAuth2AuthenticationException("invalid_token");
                    return UsernamePasswordAuthenticationToken.authenticated(UserPrincipal.from(account), token,
                        List.of(new SimpleGrantedAuthority("ROLE_" + account.role)));
                })));
        return http.build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper json, int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), new ApiError(code, message));
    }
}
