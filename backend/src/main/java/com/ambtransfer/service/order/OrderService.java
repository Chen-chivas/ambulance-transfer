package com.ambtransfer.service.order;

import com.ambtransfer.dao.db.company.CompanyMapper;
import com.ambtransfer.dao.db.order.*;
import com.ambtransfer.dto.request.*;
import com.ambtransfer.dto.response.PageResult;
import com.ambtransfer.entity.*;
import com.ambtransfer.exception.*;
import com.ambtransfer.security.UserPrincipal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderMapper orders;
    private final QuoteMapper quotes;
    private final OrderLogMapper logs;
    private final CompanyMapper companies;
    private final JdbcTemplate jdbc;

    public OrderService(OrderMapper orders, QuoteMapper quotes, OrderLogMapper logs, CompanyMapper companies, JdbcTemplate jdbc) {
        this.orders = orders; this.quotes = quotes; this.logs = logs; this.companies = companies; this.jdbc = jdbc;
    }

    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }
    public record Detail(TransferOrder order, List<Quote> quotes, List<OrderStatusLog> timeline) {}

    public PageResult<TransferOrder> list(OrderQuery query, String view, UserPrincipal user) {
        Long companyId = user.companyId();
        query.keyword = query.keyword.trim();
        LocalDate day = today();
        var items = orders.list(query, view, user.id(), companyId, day).stream().map(TransferOrder::new).toList();
        for (var item : items) {
            if (List.of("POOL", "MINE", "ASSIGNED").contains(view) && item.assignedCompanyId != null && !companyId.equals(item.assignedCompanyId)) hideAssignment(item);
            if ("DRIVER".equals(view)) { item.myQuoteId = null; item.myAmount = null; item.myQuoteStatus = null; }
        }
        return new PageResult<>(items, orders.count(query, view, user.id(), companyId, day), query.page, query.size);
    }

    private TransferOrder accessible(long id, String view, UserPrincipal user) {
        TransferOrder order = orders.find(id);
        if (order == null) throw ApiException.notFound();
        if ("MOCK".equals(view) && orders.owns(id, user.id()) == 0) throw ApiException.notFound();
        if ("DRIVER".equals(view) && (!user.id().equals(order.assignedDriverId) || !user.companyId().equals(order.assignedCompanyId))) throw ApiException.notFound();
        if ("COMPANY".equals(view) && !user.companyId().equals(order.assignedCompanyId)
            && quotes.own(id, user.companyId()) == null && orders.visibleInPool(id, user.companyId(), today()) == 0) throw ApiException.notFound();
        return order;
    }

    @Transactional(readOnly = true)
    public Detail detail(long id, String view, UserPrincipal user) {
        // Serialize the short detail read with writers so status, quotes and logs form one snapshot.
        if (orders.lock(id) == null) throw ApiException.notFound();
        var order = new TransferOrder(accessible(id, view, user));
        boolean company = "COMPANY".equals(view);
        if (company && order.assignedCompanyId != null && !user.companyId().equals(order.assignedCompanyId)) {
            hideAssignment(order);
        }
        boolean driver = "DRIVER".equals(view);
        return new Detail(order, driver ? List.of() : quotes.list(id, company ? user.companyId() : null, today()), logs.list(id, company || driver));
    }

    private void hideAssignment(TransferOrder order) {
        order.assignedVehicleId = null; order.assignedDriverId = null; order.assignedNurseId = null;
        order.assignedPlateNo = null; order.assignedDriverName = null; order.assignedNurseName = null;
    }

    @Transactional
    public TransferOrder create(OrderRequests.Create request, UserPrincipal user) {
        if (request.originAddress().trim().equals(request.destAddress().trim())) throw new BusinessException("起点与终点不能相同");
        var order = new TransferOrder();
        order.orderNo = "TR" + UUID.randomUUID().toString().replace("-", "").toUpperCase(java.util.Locale.ROOT);
        order.userName = request.userName().trim(); order.userPhone = request.userPhone().trim();
        order.originAddress = request.originAddress().trim(); order.destAddress = request.destAddress().trim();
        order.serviceLevel = request.serviceLevel(); order.patientNote = request.patientNote() == null ? null : request.patientNote().trim();
        orders.insert(order);
        log(order.id, null, "PENDING_QUOTE", "MOCK_USER", user.id(), "STATUS_CHANGE", "创建模拟转运订单");
        return orders.find(order.id);
    }

    // Order -> company -> matching vehicle is the shared lock order for quoting and selecting.
    private void ensureEligible(long companyId, String serviceLevel) {
        var company = companies.lock(companyId);
        if (company == null || !"APPROVED".equals(company.auditStatus) || company.licenseExpireAt == null || company.licenseExpireAt.isBefore(today())) {
            throw new BusinessException("车行资质未通过或已过期，暂不能报价或中标");
        }
        List<Long> available = jdbc.queryForList("SELECT TOP (1) id FROM dbo.vehicle WITH (UPDLOCK, HOLDLOCK) WHERE company_id = ? AND service_level = ? AND status = 'IDLE' ORDER BY id", Long.class, companyId, serviceLevel);
        if (available.isEmpty()) throw new BusinessException("车行暂无匹配服务等级的空闲车辆");
    }

    @Transactional
    public Quote bid(long id, OrderRequests.Bid request, UserPrincipal user) {
        var order = orders.lock(id);
        if (order == null) throw ApiException.notFound();
        if (!List.of("PENDING_QUOTE", "QUOTING").contains(order.status)) throw new BusinessException("订单已结束报价，请刷新");
        ensureEligible(user.companyId(), order.serviceLevel);
        var quote = quotes.own(id, user.companyId());
        boolean updating = quote != null;
        if (quote == null) { quote = new Quote(); quote.orderId = id; quote.companyId = user.companyId(); }
        else if (!"PENDING".equals(quote.status)) throw new BusinessException("报价已处理，不能修改");
        quote.amount = request.amount(); quote.remark = request.remark() == null ? null : request.remark().trim();
        if (updating) {
            if (quotes.update(quote) != 1) throw new BusinessException("报价已变化，请刷新");
        } else quotes.insert(quote);
        if ("PENDING_QUOTE".equals(order.status)) {
            OrderStateMachine.validate(com.ambtransfer.enums.OrderStatus.PENDING_QUOTE, com.ambtransfer.enums.OrderStatus.QUOTING);
            if (orders.startQuoting(id) != 1) throw new BusinessException("订单状态已变化，请刷新");
            log(id, "PENDING_QUOTE", "QUOTING", "DISPATCHER", user.id(), "STATUS_CHANGE", "收到首次有效报价");
        }
        log(id, "QUOTING", "QUOTING", "DISPATCHER", user.id(), "FEE_CHANGE", (updating ? "更新" : "提交") + "车行报价：" + user.companyId() + "，金额 " + quote.amount.toPlainString());
        return quotes.list(id, user.companyId(), today()).getFirst();
    }

    @Transactional
    public TransferOrder select(long id, OrderRequests.Select request, UserPrincipal user) {
        var order = orders.lock(id);
        if (order == null || orders.owns(id, user.id()) == 0) throw ApiException.notFound();
        OrderStateMachine.validate(com.ambtransfer.enums.OrderStatus.valueOf(order.status), com.ambtransfer.enums.OrderStatus.CONFIRMED);
        var winner = quotes.find(id, request.quoteId());
        if (winner == null) throw ApiException.notFound();
        if (!"PENDING".equals(winner.status)) throw new BusinessException("报价已处理，请刷新");
        if (winner.amount.compareTo(request.amount()) != 0) throw new BusinessException("报价金额已更新，请刷新后重新确认");
        ensureEligible(winner.companyId, order.serviceLevel);
        if (orders.confirm(id, winner.companyId, winner.amount) != 1) throw new BusinessException("订单已被处理，请刷新");
        quotes.settle(id, winner.id);
        log(id, "QUOTING", "CONFIRMED", "MOCK_USER", user.id(), "STATUS_CHANGE", "选定车行 " + winner.companyId + "，确认金额 " + winner.amount.toPlainString());
        return orders.find(id);
    }

    private void log(long orderId, String from, String to, String type, Long operatorId, String event, String remark) {
        var log = new OrderStatusLog();
        log.orderId = orderId; log.fromStatus = from; log.toStatus = to; log.operatorType = type; log.operatorId = operatorId; log.eventType = event; log.remark = remark;
        logs.insert(log);
    }
}
