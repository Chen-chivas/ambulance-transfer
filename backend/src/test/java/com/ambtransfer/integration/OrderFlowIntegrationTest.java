package com.ambtransfer.integration;

import com.ambtransfer.dao.db.company.CompanyMapper;
import com.ambtransfer.dao.db.staff.StaffMapper;
import com.ambtransfer.dto.request.*;
import com.ambtransfer.entity.*;
import com.ambtransfer.exception.BusinessException;
import com.ambtransfer.security.UserPrincipal;
import com.ambtransfer.service.order.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.mock-orders.enabled=true", "app.bootstrap.username=", "app.bootstrap.password="})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_SQLSERVER_TESTS", matches = "true")
class OrderFlowIntegrationTest {
    @Autowired OrderService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired CompanyMapper companies;
    @Autowired StaffMapper staff;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    private UserPrincipal user(Long companyId) {
        var s = new Staff(); s.name = "测试"; s.phone = "13800000000"; s.companyId = companyId; s.role = companyId == null ? "ADMIN" : "DISPATCHER";
        staff.insert(s); return UserPrincipal.from(s);
    }
    private RequestPostProcessor as(UserPrincipal user) { return authentication(UsernamePasswordAuthenticationToken.authenticated(user, "", List.of(new SimpleGrantedAuthority("ROLE_" + user.role())))); }
    private CarCompany company(String level, String audit) {
        var c = new CarCompany(); c.name = "测试车行"; c.licenseNo = UUID.randomUUID().toString(); c.contactPhone = "13800000000"; c.licenseExpireAt = LocalDate.of(2099, 12, 31); companies.insert(c);
        jdbc.update("UPDATE dbo.car_company SET audit_status=? WHERE id=?", audit, c.id);
        jdbc.update("INSERT INTO dbo.vehicle(company_id, plate_no, service_level) VALUES(?,?,?)", c.id, UUID.randomUUID().toString().substring(0, 18), level);
        sqlSession.clearCache(); return c;
    }
    private OrderRequests.Create input() { return new OrderRequests.Create("张先生", "13800000000", "起点医院" + suffix, "目的地医院", "BASIC", "需要轮椅"); }
    private OrderRequests.Bid bid(String value) { return new OrderRequests.Bid(new BigDecimal(value), "含基础服务"); }
    private OrderRequests.Select select(Quote quote) { return new OrderRequests.Select(quote.id, quote.amount); }

    @Test
    void creationQuoteRevisionAndSelectionKeepOrderQuotesAndLogsConsistent() throws Exception {
        var owner = user(null); var a = company("BASIC", "APPROVED"); var b = company("BASIC", "APPROVED");
        var da = user(a.id); var db = user(b.id);
        var created = mvc.perform(post("/api/mock/orders").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(input())))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_QUOTE")).andReturn();
        long id = json.readTree(created.getResponse().getContentAsByteArray()).path("id").asLong();
        assertEquals(1, service.detail(id, "MOCK", owner).timeline().size());
        var first = service.bid(id, bid("300.00"), da);
        var revised = service.bid(id, bid("350.50"), da);
        assertEquals(first.id, revised.id);
        var other = service.bid(id, bid("450.00"), db);
        mvc.perform(post("/api/mock/orders/" + id + "/select").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(select(first))))
            .andExpect(status().isConflict());
        assertEquals("QUOTING", service.detail(id, "MOCK", owner).order().status);
        mvc.perform(post("/api/mock/orders/" + id + "/select").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(select(revised))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.assignedCompanyId").value(a.id));
        var detail = service.detail(id, "MOCK", owner);
        assertEquals(0, new BigDecimal("350.50").compareTo(detail.order().finalAmount));
        assertEquals(1, detail.quotes().stream().filter(q -> "WON".equals(q.status)).count());
        assertEquals(1, detail.quotes().stream().filter(q -> "LOST".equals(q.status)).count());
        assertEquals(3, detail.timeline().stream().filter(l -> "STATUS_CHANGE".equals(l.eventType)).count());
        assertEquals(3, detail.timeline().stream().filter(l -> "FEE_CHANGE".equals(l.eventType)).count());
        assertThrows(BusinessException.class, () -> service.select(id, select(other), owner));
        assertThrows(BusinessException.class, () -> service.bid(id, bid("100"), da));
        var query = new OrderQuery(); query.keyword = suffix;
        assertEquals(0, service.list(query, "POOL", da).total());
        assertEquals("WON", service.list(query, "MINE", da).items().getFirst().myQuoteStatus);
        assertEquals("LOST", service.list(query, "MINE", db).items().getFirst().myQuoteStatus);
    }

    @Test
    void onlyApprovedCurrentCompaniesWithMatchingIdleVehiclesSeeAndBid() throws Exception {
        var owner = user(null); var order = service.create(input(), owner);
        var approved = user(company("BASIC", "APPROVED").id);
        var pending = user(company("BASIC", "PENDING").id);
        var wrongLevel = user(company("MONITORING", "APPROVED").id);
        var expiredCompany = company("BASIC", "APPROVED");
        jdbc.update("UPDATE dbo.car_company SET license_expire_at='2000-01-01' WHERE id=?", expiredCompany.id);
        var expired = user(expiredCompany.id);
        var busy = user(company("BASIC", "APPROVED").id);
        jdbc.update("UPDATE dbo.vehicle SET status='TASK' WHERE company_id=?", busy.companyId()); sqlSession.clearCache();
        var query = new OrderQuery(); query.keyword = suffix;
        assertEquals(1, service.list(query, "POOL", approved).total());
        for (var blocked : List.of(pending, wrongLevel, expired, busy)) {
            assertEquals(0, service.list(query, "POOL", blocked).total());
            mvc.perform(post("/api/company/orders/" + order.id + "/quotes").with(as(blocked)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(bid("100"))))
                .andExpect(status().isConflict());
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.quote WHERE order_id=?", Integer.class, order.id));
    }

    @Test
    void ownershipRolesAndCompetingQuotePrivacyAreEnforced() throws Exception {
        var owner = user(null); var stranger = user(null); var order = service.create(input(), owner);
        var a = user(company("BASIC", "APPROVED").id); var b = user(company("BASIC", "APPROVED").id);
        service.bid(order.id, bid("123"), a); var q = service.bid(order.id, bid("456"), b);
        mvc.perform(get("/api/mock/orders/" + order.id).with(as(stranger))).andExpect(status().isNotFound());
        mvc.perform(post("/api/mock/orders/" + order.id + "/select").with(as(stranger)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(select(q)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/mock/orders").with(as(a))).andExpect(status().isForbidden());
        var visible = service.detail(order.id, "COMPANY", a);
        assertEquals(1, visible.quotes().size()); assertEquals(a.companyId(), visible.quotes().getFirst().companyId);
        assertTrue(visible.timeline().stream().allMatch(l -> "STATUS_CHANGE".equals(l.eventType)));
        assertEquals(0, service.list(new OrderQuery(), "MOCK", stranger).total());
        var unqualified = user(company("BASIC", "PENDING").id);
        mvc.perform(get("/api/company/orders/" + order.id).with(as(unqualified))).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/orders/" + order.id).with(as(stranger))).andExpect(status().isOk()).andExpect(jsonPath("$.quotes.length()").value(2));
        var anotherOrder = service.create(input(), owner);
        mvc.perform(post("/api/mock/orders/" + anotherOrder.id + "/select").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(select(q)))).andExpect(status().isConflict());
    }

    @Test
    void selectionRechecksQualificationAndVehicleAvailability() {
        var owner = user(null); var c = company("BASIC", "APPROVED"); var dispatcher = user(c.id);
        var order = service.create(input(), owner); var quote = service.bid(order.id, bid("100"), dispatcher);
        jdbc.update("UPDATE dbo.car_company SET audit_status='PENDING' WHERE id=?", c.id); sqlSession.clearCache();
        assertThrows(BusinessException.class, () -> service.select(order.id, select(quote), owner));
        jdbc.update("UPDATE dbo.car_company SET audit_status='APPROVED' WHERE id=?", c.id);
        jdbc.update("UPDATE dbo.vehicle SET status='MAINTENANCE' WHERE company_id=?", c.id); sqlSession.clearCache();
        assertFalse(service.detail(order.id, "MOCK", owner).quotes().getFirst().eligible);
        assertThrows(BusinessException.class, () -> service.select(order.id, select(quote), owner));
        assertEquals("QUOTING", jdbc.queryForObject("SELECT status FROM dbo.transfer_order WHERE id=?", String.class, order.id));
    }

    @Test
    void requestValidationRejectsInvalidOrdersPricesAndPaging() throws Exception {
        var owner = user(null); var order = service.create(input(), owner); var dispatcher = user(company("BASIC", "APPROVED").id);
        for (String amount : List.of("0", "-1", "0.001", "10000000000")) {
            mvc.perform(post("/api/company/orders/" + order.id + "/quotes").with(as(dispatcher)).contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/mock/orders").with(as(owner)).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(get("/api/company/orders/pool?page=0").with(as(dispatcher))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/company/orders/pool?serviceLevel=BAD").with(as(dispatcher))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/mock/orders")).andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void tenConcurrentSelectionsProduceExactlyOneWinnerAndOneConfirmationLog() throws Exception {
        var owner = user(null); var ca = company("BASIC", "APPROVED"); var cb = company("BASIC", "APPROVED");
        var da = user(ca.id); var db = user(cb.id); TransferOrder order = null;
        var executor = Executors.newFixedThreadPool(10);
        try {
            order = service.create(input(), owner);
            var a = service.bid(order.id, bid("100"), da); var b = service.bid(order.id, bid("200"), db);
            long orderId = order.id;
            var start = new CountDownLatch(1); var ready = new CountDownLatch(10);
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                var selected = i % 2 == 0 ? a : b;
                attempts.add(executor.submit(() -> { ready.countDown(); start.await(); try { service.select(orderId, select(selected), owner); return true; } catch (BusinessException conflict) { return false; } }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            int successes = 0;
            for (var attempt : attempts) if (attempt.get(30, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.quote WHERE order_id=? AND status='WON'", Integer.class, orderId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.order_status_log WHERE order_id=? AND to_status='CONFIRMED'", Integer.class, orderId));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.transfer_order o JOIN dbo.quote q ON q.order_id=o.id AND q.status='WON' WHERE o.id=? AND o.assigned_company_id=q.company_id AND o.final_amount=q.amount AND o.status='CONFIRMED'", Integer.class, orderId));
        } finally {
            executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS);
            if (order != null) {
                jdbc.update("DELETE FROM dbo.order_status_log WHERE order_id=?", order.id);
                jdbc.update("DELETE FROM dbo.quote WHERE order_id=?", order.id);
                jdbc.update("DELETE FROM dbo.transfer_order WHERE id=?", order.id);
            }
            for (Long companyId : List.of(ca.id, cb.id)) {
                jdbc.update("DELETE FROM dbo.staff WHERE company_id=?", companyId);
                jdbc.update("DELETE FROM dbo.vehicle WHERE company_id=?", companyId);
                jdbc.update("DELETE FROM dbo.car_company WHERE id=?", companyId);
            }
            jdbc.update("DELETE FROM dbo.staff WHERE id=?", owner.id());
        }
    }
}
