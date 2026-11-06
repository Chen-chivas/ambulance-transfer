package com.ambtransfer.controller;

import com.ambtransfer.dao.db.system.DatabaseHealthMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
@org.springframework.context.annotation.Import(com.ambtransfer.config.SecurityConfig.class)
class HealthControllerTest {
    @MockitoBean
    private com.ambtransfer.dao.db.staff.StaffMapper staffMapper;
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DatabaseHealthMapper databaseHealthMapper;

    @Test
    void livenessDoesNotRequireDatabase() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void readinessChecksTargetDatabase() throws Exception {
        when(databaseHealthMapper.databaseName()).thenReturn("ambulance_transfer");
        mockMvc.perform(get("/api/health/db")).andExpect(status().isOk());
        when(databaseHealthMapper.databaseName()).thenReturn("master");
        mockMvc.perform(get("/api/health/db")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void readinessReportsUnavailableWithoutLeakingDetails() throws Exception {
        when(databaseHealthMapper.databaseName()).thenThrow(new DataAccessResourceFailureException("secret"));
        mockMvc.perform(get("/api/health/db"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("SQL Server 连接失败，请检查连接配置及数据库服务"));
    }
}
