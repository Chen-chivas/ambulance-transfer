package com.ambtransfer.integration;

import com.ambtransfer.dao.db.staff.StaffMapper;
import com.ambtransfer.entity.Staff;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses the initialized SQL Server schema; all test rows are rolled back. */
@SpringBootTest(properties = {"app.bootstrap.username=", "app.bootstrap.password="})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_TESTS", matches = "true")
class BusinessIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StaffMapper staff;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtEncoder encoder;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    private String admin;
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);
    private final String password = "Integration-Password-42";

    @BeforeEach
    void initializeAdmin() throws Exception {
        Staff account = account(null, "ADMIN", "admin_" + suffix);
        staff.insert(account);
        admin = login(account.username);
    }

    private Staff account(Long companyId, String role, String username) {
        var value = new Staff();
        value.companyId = companyId; value.role = role; value.name = "集成测试"; value.phone = "13800000000";
        value.username = username; value.passwordHash = passwords.encode(password);
        return value;
    }

    private String login(String username) throws Exception {
        return body(call(post("/api/auth/login"), null, Map.of("username", username, "password", password))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.passwordHash").doesNotExist())).path("token").asText();
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token, Object data) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (data != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(data));
        return mvc.perform(request);
    }

    private JsonNode body(ResultActions result) throws Exception { return json.readTree(result.andReturn().getResponse().getContentAsByteArray()); }

    private long company(String name) throws Exception {
        return body(call(post("/api/admin/companies"), admin, companyInput(name, "2099-12-31"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.auditStatus").value("PENDING"))).path("id").asLong();
    }

    private Map<String, Object> companyInput(String name, String expiry) {
        return Map.of("name", name, "licenseNo", "LICENSE-" + suffix + name, "licenseExpireAt", expiry, "contactPhone", "13800000000");
    }

    private Map<String, String> vehicleInput(String plate, String state) { return Map.of("plateNo", plate, "serviceLevel", "MONITORING", "status", state); }

    @Test
    void companyAuditAndVehicleLifecyclePersistInSqlServer() throws Exception {
        long company = company("车行甲");
        String base = "/api/admin/companies/" + company;
        call(post(base + "/audit"), admin, Map.of("status", "REJECTED", "remark", "  ")).andExpect(status().isConflict());
        call(post(base + "/audit"), admin, Map.of("status", "REJECTED", "remark", "证书不完整")).andExpect(status().isOk()).andExpect(jsonPath("$.auditRemark").value("证书不完整"));
        call(post(base + "/audit"), admin, Map.of("status", "APPROVED")).andExpect(status().isConflict());
        call(put(base), admin, companyInput("车行甲", "2099-12-31")).andExpect(status().isOk()).andExpect(jsonPath("$.auditStatus").value("PENDING"));
        call(post(base + "/audit"), admin, Map.of("status", "APPROVED")).andExpect(status().isOk());
        call(get("/api/admin/overview"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.companies").isNumber()).andExpect(jsonPath("$.pending").isNumber()).andExpect(jsonPath("$.idleVehicles").isNumber());
        long vehicle = body(call(post(base + "/vehicles"), admin, vehicleInput("测" + suffix, "IDLE")).andExpect(status().isCreated())).path("id").asLong();
        call(put(base + "/vehicles/" + vehicle), admin, vehicleInput("测" + suffix, "MAINTENANCE")).andExpect(status().isOk());
        assertEquals("MAINTENANCE", jdbc.queryForObject("SELECT status FROM dbo.vehicle WHERE id = ?", String.class, vehicle));
        call(get(base + "/vehicles"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        call(delete(base), admin, null).andExpect(status().isConflict());
        call(delete(base + "/vehicles/" + vehicle), admin, null).andExpect(status().isNoContent());
        call(delete(base), admin, null).andExpect(status().isNoContent());
        call(get(base), admin, null).andExpect(status().isNotFound());
    }

    @Test
    void dispatchersCannotAccessOtherCompaniesOrAdminRoutes() throws Exception {
        long a = company("隔离甲"); long b = company("隔离乙");
        String aBase = "/api/admin/companies/" + a;
        call(post(aBase + "/dispatchers"), admin, Map.of("name", "调度甲", "username", "dispatcher_" + suffix, "password", password, "phone", "13800000000"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        long otherVehicle = body(call(post("/api/admin/companies/" + b + "/vehicles"), admin, vehicleInput("隔" + suffix, "IDLE")).andExpect(status().isCreated())).path("id").asLong();
        String dispatcher = login("dispatcher_" + suffix);
        call(get("/api/admin/companies"), dispatcher, null).andExpect(status().isForbidden());
        call(get("/api/company/profile"), dispatcher, null).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(a));
        call(get("/api/company/vehicles?companyId=" + b), dispatcher, null).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        call(put("/api/company/vehicles/" + otherVehicle), dispatcher, vehicleInput("隔" + suffix, "MAINTENANCE")).andExpect(status().isNotFound());
        call(delete("/api/company/vehicles/" + otherVehicle), dispatcher, null).andExpect(status().isNotFound());
        var payload = new java.util.HashMap<String, Object>(vehicleInput("本" + suffix, "IDLE")); payload.put("companyId", b);
        call(post("/api/company/vehicles"), dispatcher, payload).andExpect(status().isCreated()).andExpect(jsonPath("$.companyId").value(a));
    }

    @Test
    void expiredQualificationsCannotBeApprovedAndExpireInQueries() throws Exception {
        long id = company("过期");
        String base = "/api/admin/companies/" + id;
        call(put(base), admin, companyInput("过期", "2000-01-01")).andExpect(status().isOk());
        call(post(base + "/audit"), admin, Map.of("status", "APPROVED")).andExpect(status().isConflict());
        jdbc.update("UPDATE dbo.car_company SET audit_status='APPROVED' WHERE id=?", id);
        sqlSession.clearCache();
        call(get(base), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.auditStatus").value("EXPIRED"));
        call(get("/api/admin/companies?status=EXPIRED&keyword=" + suffix), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void duplicateRecordsAndInvalidPaginationHaveUsefulResponses() throws Exception {
        long a = company("唯一甲"); long b = company("唯一乙");
        call(post("/api/admin/companies"), admin, companyInput("唯一甲", "2099-12-31")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_RECORD"));
        call(post("/api/admin/companies/" + a + "/vehicles"), admin, vehicleInput("唯" + suffix, "IDLE")).andExpect(status().isCreated());
        call(post("/api/admin/companies/" + b + "/vehicles"), admin, vehicleInput("唯" + suffix, "IDLE")).andExpect(status().isConflict());
        call(get("/api/admin/companies?page=0"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/admin/companies?size=101"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/admin/companies?status=unknown"), admin, null).andExpect(status().isBadRequest());
        call(get("/api/admin/companies?keyword=%25"), admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void busyVehicleCannotBeModifiedDeletedOrCreatedByClient() throws Exception {
        long c = company("任务"); String base = "/api/admin/companies/" + c + "/vehicles";
        long id = body(call(post(base), admin, vehicleInput("任" + suffix, "IDLE")).andExpect(status().isCreated())).path("id").asLong();
        jdbc.update("UPDATE dbo.vehicle SET status='TASK' WHERE id=?", id);
        call(put(base + "/" + id), admin, vehicleInput("任" + suffix, "IDLE")).andExpect(status().isConflict());
        call(delete(base + "/" + id), admin, null).andExpect(status().isConflict());
        call(post(base), admin, vehicleInput("坏" + suffix, "TASK")).andExpect(status().isBadRequest());
    }

    @Test
    void tokensAreValidatedAndDisabledUsersImmediatelyLoseAccess() throws Exception {
        call(get("/api/admin/companies"), null, null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        call(get("/api/admin/companies"), "forged", null).andExpect(status().isUnauthorized());
        call(post("/api/auth/login"), null, Map.of("username", "admin_" + suffix, "password", "incorrect")).andExpect(status().isUnauthorized());
        var claims = JwtClaimsSet.builder().issuer("ambulance-transfer").subject("1").issuedAt(Instant.now().minusSeconds(7200)).expiresAt(Instant.now().minusSeconds(3600)).build();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        call(get("/api/auth/me"), expired, null).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE dbo.staff SET enabled=0 WHERE username=?", "admin_" + suffix);
        sqlSession.clearCache();
        call(get("/api/auth/me"), admin, null).andExpect(status().isUnauthorized());
    }
}
