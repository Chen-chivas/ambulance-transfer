package com.ambtransfer.controller;

import com.ambtransfer.config.SecurityConfig;
import com.ambtransfer.controller.mock.MockOrderController;
import com.ambtransfer.dao.db.staff.StaffMapper;
import com.ambtransfer.service.order.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = MockOrderController.class, properties = "app.mock-orders.enabled=false")
@Import(SecurityConfig.class)
class MockOrderDisabledTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;
    @MockitoBean StaffMapper staff;

    @Test @WithMockUser(roles = "ADMIN")
    void disabledMockDoesNotAccessBusinessData() throws Exception {
        mvc.perform(get("/api/mock/orders")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MOCK_DISABLED"));
        verifyNoInteractions(orders);
    }
}
