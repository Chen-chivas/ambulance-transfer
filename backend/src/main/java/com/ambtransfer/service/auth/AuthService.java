package com.ambtransfer.service.auth;

import com.ambtransfer.config.SecurityConfig;
import com.ambtransfer.dao.db.staff.StaffMapper;
import com.ambtransfer.dto.request.Requests;
import com.ambtransfer.exception.ApiException;
import com.ambtransfer.security.UserPrincipal;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final StaffMapper staff;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwt;
    private final String dummyHash;

    public AuthService(StaffMapper staff, PasswordEncoder passwords, JwtEncoder jwt) {
        this.staff = staff;
        this.passwords = passwords;
        this.jwt = jwt;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public record LoginResult(String token, Instant expiresAt, UserPrincipal user) {}

    public LoginResult login(Requests.Login request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "账号或密码错误，或账号已停用");
        }
        var account = staff.findByUsername(request.username().trim());
        boolean matched = passwords.matches(request.password(), account == null ? dummyHash : account.passwordHash);
        if (account == null || !matched || !account.enabled) {
            throw new ApiException(401, "INVALID_CREDENTIALS", "账号或密码错误，或账号已停用");
        }
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(7200);
        var claims = JwtClaimsSet.builder().issuer(SecurityConfig.ISSUER).subject(account.id.toString())
            .issuedAt(now).expiresAt(expires).id(UUID.randomUUID().toString()).build();
        var token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return new LoginResult(token.getTokenValue(), expires, UserPrincipal.from(account));
    }
}
