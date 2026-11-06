package com.ambtransfer.integration;

import com.ambtransfer.dao.db.company.*;
import com.ambtransfer.dao.db.staff.StaffMapper;
import com.ambtransfer.dto.request.*;
import com.ambtransfer.entity.*;
import com.ambtransfer.exception.*;
import com.ambtransfer.security.UserPrincipal;
import com.ambtransfer.service.company.StaffService;
import com.ambtransfer.service.order.*;
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

@SpringBootTest(properties={"app.mock-orders.enabled=true", "app.bootstrap.username=", "app.bootstrap.password="})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_SQLSERVER_TESTS", matches="true")
class DispatchIntegrationTest {
    @Autowired OrderService orders;
    @Autowired DispatchService dispatch;
    @Autowired StaffService people;
    @Autowired StaffMapper staff;
    @Autowired CompanyMapper companies;
    @Autowired VehicleMapper vehicles;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired org.mybatis.spring.SqlSessionTemplate sqlSession;
    private final String password="Integration-Password-42";

    private Staff account(Long companyId, String role) {
        var s=new Staff(); s.companyId=companyId; s.role=role; s.name="测试人员"; s.phone="13800000000"; staff.insert(s); return s;
    }
    private UserPrincipal owner() { return UserPrincipal.from(account(null, "ADMIN")); }
    private long company() {
        var c=new CarCompany(); c.name="派车测试"; c.licenseNo=UUID.randomUUID().toString(); c.contactPhone="13800000000"; c.licenseExpireAt=LocalDate.of(2099,12,31); companies.insert(c);
        companies.audit(c.id, "APPROVED", null); return c.id;
    }
    private long vehicle(long c, String level) {
        var v=new Vehicle(); v.companyId=c; v.plateNo=UUID.randomUUID().toString().substring(0,18); v.serviceLevel=level; v.status="IDLE"; vehicles.insert(v); return v.id;
    }
    private long person(long c, String role) {
        return people.create(c,new StaffRequests.Create("测试"+role,"13800000000",role,"CERT",LocalDate.of(2099,12,31),"DRIVER".equals(role)?"d_"+UUID.randomUUID().toString().substring(0,8):null,"DRIVER".equals(role)?password:null)).id();
    }
    private UserPrincipal driver(long id) { return UserPrincipal.from(staff.findById(id)); }
    private RequestPostProcessor as(UserPrincipal user) { return authentication(UsernamePasswordAuthenticationToken.authenticated(user,"",List.of(new SimpleGrantedAuthority("ROLE_"+user.role())))); }
    private TransferOrder confirmed(UserPrincipal owner, UserPrincipal company, String level) {
        var order=orders.create(new OrderRequests.Create("联系人","13800000000","起点","终点",level,"需要陪护"),owner);
        var quote=orders.bid(order.id,new OrderRequests.Bid(new BigDecimal("350.50"),"基础服务"),company);
        return orders.select(order.id,new OrderRequests.Select(quote.id,quote.amount),owner);
    }
    private StaffRequests.Update update(boolean enabled) { return new StaffRequests.Update("修改姓名","13800000000","CERT",LocalDate.of(2099,12,31),enabled); }

    @Test
    void threeOrdersRunThroughAllDriverStepsAndReleaseResourcesWithCompleteTimelines() throws Exception {
        long c=company(), v=vehicle(c,"MONITORING"), d=person(c,"DRIVER"), n=person(c,"NURSE");
        var owner=owner(); var company=UserPrincipal.from(account(c,"DISPATCHER")); var driver=driver(d);
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("username",driver.username(),"password",password))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("DRIVER")).andExpect(jsonPath("$.user.passwordHash").doesNotExist());
        for(int i=0;i<3;i++) {
            var order=confirmed(owner,company,"MONITORING");
            var resources=dispatch.resources(order.id,company); assertEquals(1,resources.drivers().size()); assertEquals(1,resources.nurses().size());
            mvc.perform(post("/api/company/orders/"+order.id+"/dispatch").with(as(company)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(new DispatchRequest(v,d,n))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISPATCHED")).andExpect(jsonPath("$.assignedDriverId").value(d));
            assertEquals("TASK",jdbc.queryForObject("SELECT status FROM dbo.vehicle WHERE id=?",String.class,v));
            assertTrue(people.list(c,"","DRIVER",1,10).items().getFirst().busy());
            assertThrows(BusinessException.class,()->people.update(c,d,update(false)));
            mvc.perform(post("/api/driver/tasks/"+order.id+"/complete").with(as(driver))).andExpect(status().isConflict());
            for(String action:List.of("depart","arrive","pickup","complete")) {
                mvc.perform(post("/api/driver/tasks/"+order.id+"/"+action).with(as(driver))).andExpect(status().isOk());
                mvc.perform(post("/api/driver/tasks/"+order.id+"/"+action).with(as(driver))).andExpect(status().isConflict());
            }
            var detail=orders.detail(order.id,"ADMIN",owner);
            assertEquals("COMPLETED",detail.order().status); assertNotNull(detail.order().assignedPlateNo);
            assertEquals(List.of("PENDING_QUOTE","QUOTING","CONFIRMED","DISPATCHED","EN_ROUTE","ARRIVED","ON_BOARD","COMPLETED"),detail.timeline().stream().filter(l->"STATUS_CHANGE".equals(l.eventType)).map(l->l.toStatus).toList());
            var timeline=detail.timeline(); for(int j=1;j<timeline.size();j++) assertFalse(timeline.get(j).createdAt.isBefore(timeline.get(j-1).createdAt));
            assertEquals("IDLE",jdbc.queryForObject("SELECT status FROM dbo.vehicle WHERE id=?",String.class,v));
            assertFalse(people.list(c,"","DRIVER",1,10).items().getFirst().busy());
        }
        mvc.perform(delete("/api/company/staff/"+d).with(as(company))).andExpect(status().isConflict());
    }

    @Test
    void dispatchRechecksCompanyVehicleRoleExpiryEnabledAndMonitoringNurse() throws Exception {
        long c=company(), v=vehicle(c,"MONITORING"), wrong=vehicle(c,"BASIC"), d=person(c,"DRIVER"), n=person(c,"NURSE");
        var company=UserPrincipal.from(account(c,"DISPATCHER")); var order=confirmed(owner(),company,"MONITORING");
        assertThrows(BusinessException.class,()->dispatch.dispatch(order.id,new DispatchRequest(v,d,null),company));
        assertThrows(BusinessException.class,()->dispatch.dispatch(order.id,new DispatchRequest(wrong,d,n),company));
        assertThrows(BusinessException.class,()->dispatch.dispatch(order.id,new DispatchRequest(v,n,d),company));
        people.update(c,d,update(false));
        assertEquals(0,dispatch.resources(order.id,company).drivers().size());
        assertThrows(BusinessException.class,()->dispatch.dispatch(order.id,new DispatchRequest(v,d,n),company));
        people.update(c,d,update(true));
        jdbc.update("UPDATE dbo.staff SET cert_expire_at='2000-01-01' WHERE id=?",n); sqlSession.clearCache();
        assertThrows(BusinessException.class,()->dispatch.dispatch(order.id,new DispatchRequest(v,d,n),company));
        jdbc.update("UPDATE dbo.staff SET cert_expire_at='2099-12-31' WHERE id=?",n);
        jdbc.update("UPDATE dbo.car_company SET license_expire_at='2000-01-01' WHERE id=?",c); sqlSession.clearCache();
        assertThrows(BusinessException.class,()->dispatch.dispatch(order.id,new DispatchRequest(v,d,n),company));
        assertEquals("IDLE",jdbc.queryForObject("SELECT status FROM dbo.vehicle WHERE id=?",String.class,v));
        assertEquals("CONFIRMED",jdbc.queryForObject("SELECT status FROM dbo.transfer_order WHERE id=?",String.class,order.id));
        mvc.perform(post("/api/company/orders/"+order.id+"/dispatch").with(as(company)).contentType(MediaType.APPLICATION_JSON).content("{}" )).andExpect(status().isBadRequest());
    }

    @Test
    void companyAndDriverOwnershipAndStaffCredentialsNeverLeak() throws Exception {
        long c=company(), other=company(), v=vehicle(c,"BASIC"), d=person(c,"DRIVER"), stranger=person(other,"DRIVER");
        var company=UserPrincipal.from(account(c,"DISPATCHER")); var otherCompany=UserPrincipal.from(account(other,"DISPATCHER"));
        vehicle(other,"BASIC");
        var owner=owner();
        var created=orders.create(new OrderRequests.Create("联系人","13800000000","起点","终点","BASIC",""),owner);
        var won=orders.bid(created.id,new OrderRequests.Bid(new BigDecimal("350.50"),"中标"),company);
        orders.bid(created.id,new OrderRequests.Bid(new BigDecimal("400.00"),"落标"),otherCompany);
        var order=orders.select(created.id,new OrderRequests.Select(won.id,won.amount),owner);
        mvc.perform(get("/api/company/orders/"+order.id+"/resources").with(as(otherCompany))).andExpect(status().isNotFound());
        assertThrows(ApiException.class,()->dispatch.dispatch(order.id,new DispatchRequest(v,d,null),otherCompany));
        assertThrows(BusinessException.class,()->dispatch.dispatch(order.id,new DispatchRequest(v,stranger,null),company));
        dispatch.dispatch(order.id,new DispatchRequest(v,d,null),company);
        var losingDetail=orders.detail(order.id,"COMPANY",otherCompany).order();
        assertNull(losingDetail.assignedVehicleId); assertNull(losingDetail.assignedDriverName);
        var losingList=orders.list(new OrderQuery(),"MINE",otherCompany).items().getFirst();
        assertNull(losingList.assignedDriverId); assertNull(losingList.assignedPlateNo);
        var driverList=orders.list(new OrderQuery(),"DRIVER",driver(d)).items().getFirst();
        assertNull(driverList.myAmount); assertNull(driverList.myQuoteId);
        mvc.perform(get("/api/driver/tasks/"+order.id).with(as(driver(stranger)))).andExpect(status().isNotFound());
        mvc.perform(post("/api/driver/tasks/"+order.id+"/depart").with(as(driver(stranger)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/driver/tasks").with(as(driver(stranger)))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/driver/tasks/"+order.id).with(as(driver(d)))).andExpect(status().isOk()).andExpect(jsonPath("$.quotes.length()").value(0));
        mvc.perform(get("/api/company/staff").with(as(company))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].passwordHash").doesNotExist()).andExpect(jsonPath("$.items[0].password").doesNotExist());
        mvc.perform(put("/api/company/staff/"+stranger).with(as(company)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(update(false)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/company/staff").with(as(driver(d)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/company/orders/assigned").with(as(otherCompany))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void activeTasksCanFinishAfterQualificationsExpireButBusyNursesCannotBeReused() {
        long c=company(), v=vehicle(c,"MONITORING"), v2=vehicle(c,"MONITORING"), d=person(c,"DRIVER"), d2=person(c,"DRIVER"), n=person(c,"NURSE");
        var owner=owner(); var company=UserPrincipal.from(account(c,"DISPATCHER"));
        var first=confirmed(owner,company,"MONITORING"); var second=confirmed(owner,company,"MONITORING");
        dispatch.dispatch(first.id,new DispatchRequest(v,d,n),company);
        assertThrows(BusinessException.class,()->dispatch.dispatch(second.id,new DispatchRequest(v2,d2,n),company));
        assertThrows(BusinessException.class,()->people.delete(c,n));
        jdbc.update("UPDATE dbo.car_company SET license_expire_at='2000-01-01' WHERE id=?",c);
        jdbc.update("UPDATE dbo.staff SET cert_expire_at='2000-01-01' WHERE id=?",d); sqlSession.clearCache();
        for(var action:List.of("depart","arrive","pickup","complete")) dispatch.advance(first.id,action,driver(d));
        assertEquals(0,staff.activeTasks(n));
        assertEquals("IDLE",jdbc.queryForObject("SELECT status FROM dbo.vehicle WHERE id=?",String.class,v));
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentOrdersCannotClaimTheSameVehicleDriverOrNurse() throws Exception {
        long c=company(); UserPrincipal owner=null;
        var orderIds=new ArrayList<Long>(); var executor=Executors.newFixedThreadPool(2);
        try {
            long v1=vehicle(c,"MONITORING"), v2=vehicle(c,"MONITORING"), d1=person(c,"DRIVER"), d2=person(c,"DRIVER"), n1=person(c,"NURSE"), n2=person(c,"NURSE");
            owner=owner(); var company=UserPrincipal.from(account(c,"DISPATCHER"));
            for(String shared:List.of("vehicle","driver","nurse")) {
                var a=confirmed(owner,company,"MONITORING"); orderIds.add(a.id); var b=confirmed(owner,company,"MONITORING"); orderIds.add(b.id);
                var first=new DispatchRequest(v1,d1,n1);
                var second=new DispatchRequest("vehicle".equals(shared)?v1:v2,"driver".equals(shared)?d1:d2,"nurse".equals(shared)?n1:n2);
                var start=new CountDownLatch(1);
                var fa=executor.submit(()->{start.await();try{dispatch.dispatch(a.id,first,company);return true;}catch(BusinessException ex){return false;}});
                var fb=executor.submit(()->{start.await();try{dispatch.dispatch(b.id,second,company);return true;}catch(BusinessException ex){return false;}});
                start.countDown(); boolean successA=fa.get(30,TimeUnit.SECONDS), successB=fb.get(30,TimeUnit.SECONDS);
                assertNotEquals(successA,successB,"only one dispatch may claim the shared "+shared);
                var winner=successA?a:b; var chosen=successA?first:second;
                assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM dbo.transfer_order WHERE id IN (?,?) AND status='DISPATCHED'",Integer.class,a.id,b.id));
                for(var action:List.of("depart","arrive","pickup","complete")) dispatch.advance(winner.id,action,driver(chosen.driverId()));
                assertEquals("IDLE",jdbc.queryForObject("SELECT status FROM dbo.vehicle WHERE id=?",String.class,chosen.vehicleId()));
            }
        } finally {
            executor.shutdownNow(); executor.awaitTermination(10,TimeUnit.SECONDS);
            for(long id:orderIds) { jdbc.update("DELETE FROM dbo.order_status_log WHERE order_id=?",id); jdbc.update("DELETE FROM dbo.quote WHERE order_id=?",id); jdbc.update("DELETE FROM dbo.transfer_order WHERE id=?",id); }
            jdbc.update("DELETE FROM dbo.staff WHERE company_id=?",c); jdbc.update("DELETE FROM dbo.vehicle WHERE company_id=?",c); jdbc.update("DELETE FROM dbo.car_company WHERE id=?",c); if(owner!=null) jdbc.update("DELETE FROM dbo.staff WHERE id=?",owner.id());
        }
    }
}
