package com.ambtransfer.service.order;

import com.ambtransfer.dao.db.company.*;
import com.ambtransfer.dao.db.order.*;
import com.ambtransfer.dao.db.staff.StaffMapper;
import com.ambtransfer.dto.request.DispatchRequest;
import com.ambtransfer.dto.response.StaffSummary;
import com.ambtransfer.entity.*;
import com.ambtransfer.enums.OrderStatus;
import com.ambtransfer.exception.*;
import com.ambtransfer.security.UserPrincipal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchService {
    private final OrderMapper orders;
    private final CompanyMapper companies;
    private final VehicleMapper vehicles;
    private final StaffMapper staff;
    private final OrderLogMapper logs;
    private final JdbcTemplate jdbc;
    public DispatchService(OrderMapper orders, CompanyMapper companies, VehicleMapper vehicles, StaffMapper staff, OrderLogMapper logs, JdbcTemplate jdbc) {
        this.orders=orders; this.companies=companies; this.vehicles=vehicles; this.staff=staff; this.logs=logs; this.jdbc=jdbc;
    }
    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }
    public record Resources(List<Vehicle> vehicles, List<StaffSummary> drivers, List<StaffSummary> nurses) {}

    private TransferOrder assigned(long id, UserPrincipal user) {
        var order = orders.lock(id);
        if (order == null || !user.companyId().equals(order.assignedCompanyId)) throw ApiException.notFound();
        return order;
    }
    private void eligibleCompany(long companyId) {
        var company = companies.lock(companyId);
        if (company == null || !"APPROVED".equals(company.auditStatus) || company.licenseExpireAt == null || company.licenseExpireAt.isBefore(today())) throw new BusinessException("车行资质未通过或已过期，暂不能派车");
    }

    @Transactional(readOnly = true)
    public Resources resources(long id, UserPrincipal user) {
        var order = assigned(id, user);
        if (!"CONFIRMED".equals(order.status)) throw new BusinessException("仅已确认的待派车订单可选择资源");
        eligibleCompany(user.companyId());
        var available = jdbc.query("SELECT id, company_id, plate_no, service_level, status FROM dbo.vehicle WHERE company_id=? AND service_level=? AND status='IDLE' ORDER BY id",
            (rs, row) -> { var v = new Vehicle(); v.id=rs.getLong("id"); v.companyId=rs.getLong("company_id"); v.plateNo=rs.getString("plate_no"); v.serviceLevel=rs.getString("service_level"); v.status=rs.getString("status"); return v; }, user.companyId(), order.serviceLevel);
        var people = staff.available(user.companyId(), today()).stream().map(StaffSummary::from).toList();
        return new Resources(available, people.stream().filter(s -> "DRIVER".equals(s.role())).toList(), people.stream().filter(s -> "NURSE".equals(s.role())).toList());
    }

    private void validStaff(long companyId, long id, String role) {
        var person = staff.lock(companyId, id);
        if (person == null || !role.equals(person.role)) throw new BusinessException("请选择本车行正确角色的人员");
        if (!person.enabled || person.certNo == null || person.certNo.isBlank() || person.certExpireAt == null || person.certExpireAt.isBefore(today())) throw new BusinessException("人员已停用或证书无效，请更新资料后重试");
        if ("DRIVER".equals(role) && (person.username == null || person.passwordHash == null)) throw new BusinessException("司机未配置登录账号");
        if (staff.activeTasks(id) > 0) throw new BusinessException("所选人员正在执行其他任务，请重新选择");
    }

    @Transactional
    public TransferOrder dispatch(long id, DispatchRequest request, UserPrincipal user) {
        var order = assigned(id, user);
        OrderStateMachine.validate(OrderStatus.valueOf(order.status), OrderStatus.DISPATCHED);
        // Same lock order as quoting/selection: order -> company -> vehicle -> staff.
        eligibleCompany(user.companyId());
        var vehicle = vehicles.lock(user.companyId(), request.vehicleId());
        if (vehicle == null || !order.serviceLevel.equals(vehicle.serviceLevel) || !"IDLE".equals(vehicle.status)) throw new BusinessException("车辆不属于本车行、等级不匹配或已被占用");
        validStaff(user.companyId(), request.driverId(), "DRIVER");
        if ("MONITORING".equals(order.serviceLevel) && request.nurseId() == null) throw new BusinessException("监护型转运必须分配护士");
        if (request.nurseId() != null) validStaff(user.companyId(), request.nurseId(), "NURSE");
        if (jdbc.update("UPDATE dbo.vehicle SET status='TASK', updated_at=SYSUTCDATETIME() WHERE id=? AND company_id=? AND status='IDLE'", request.vehicleId(), user.companyId()) != 1) throw new BusinessException("车辆已被占用，请刷新");
        if (orders.dispatch(id, user.companyId(), request.vehicleId(), request.driverId(), request.nurseId()) != 1) throw new BusinessException("订单已被处理，请刷新");
        log(order, "DISPATCHED", user, "派车成功：车辆 " + vehicle.plateNo + "，司机 " + request.driverId() + (request.nurseId() == null ? "" : "，护士 " + request.nurseId()));
        return orders.find(id);
    }

    @Transactional
    public TransferOrder advance(long id, String action, UserPrincipal user) {
        String from; String to; String remark;
        switch (action) {
            case "depart" -> { from="DISPATCHED"; to="EN_ROUTE"; remark="司机已出发"; }
            case "arrive" -> { from="EN_ROUTE"; to="ARRIVED"; remark="司机已到达上车点"; }
            case "pickup" -> { from="ARRIVED"; to="ON_BOARD"; remark="患者已上车"; }
            case "complete" -> { from="ON_BOARD"; to="COMPLETED"; remark="患者已送达，转运完成"; }
            default -> throw new ApiException(404, "NOT_FOUND", "任务操作不存在");
        }
        var order = assigned(id, user);
        if (!user.id().equals(order.assignedDriverId)) throw ApiException.notFound();
        if (!from.equals(order.status)) throw new BusinessException("任务状态已变化，请刷新后按顺序操作");
        OrderStateMachine.validate(OrderStatus.valueOf(order.status), OrderStatus.valueOf(to));
        if (companies.lock(user.companyId()) == null) throw ApiException.notFound();
        // Existing tasks may finish even if company/person certificates expire after dispatch.
        if ("COMPLETED".equals(to)) {
            var vehicle = vehicles.lock(user.companyId(), order.assignedVehicleId);
            if (vehicle == null || !"TASK".equals(vehicle.status)) throw new BusinessException("任务车辆状态异常，请联系调度员");
        }
        if (orders.advance(id, user.companyId(), user.id(), from, to) != 1) throw new BusinessException("任务已被处理，请刷新");
        if ("COMPLETED".equals(to) && jdbc.update("UPDATE dbo.vehicle SET status='IDLE', updated_at=SYSUTCDATETIME() WHERE id=? AND company_id=? AND status='TASK'", order.assignedVehicleId, user.companyId()) != 1) throw new BusinessException("释放车辆失败，请联系调度员");
        log(order, to, user, remark);
        return orders.find(id);
    }

    private void log(TransferOrder order, String to, UserPrincipal user, String remark) {
        var log = new OrderStatusLog(); log.orderId=order.id; log.fromStatus=order.status; log.toStatus=to;
        log.operatorType=user.role(); log.operatorId=user.id(); log.eventType="STATUS_CHANGE"; log.remark=remark;
        logs.insert(log);
    }
}
