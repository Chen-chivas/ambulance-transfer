package com.ambtransfer.controller.admin;

import com.ambtransfer.dto.request.OrderQuery;
import com.ambtransfer.dto.response.PageResult;
import com.ambtransfer.entity.TransferOrder;
import com.ambtransfer.security.UserPrincipal;
import com.ambtransfer.service.order.OrderService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminOrderController {
    private final OrderService orders;
    private final boolean mockEnabled;
    public AdminOrderController(OrderService orders, @Value("${app.mock-orders.enabled:false}") boolean mockEnabled) { this.orders = orders; this.mockEnabled = mockEnabled; }

    @GetMapping("/features")
    public Map<String, Boolean> features() { return Map.of("mockOrdersEnabled", mockEnabled); }

    @GetMapping("/orders")
    public PageResult<TransferOrder> list(@Valid @ModelAttribute OrderQuery query, @AuthenticationPrincipal UserPrincipal user) { return orders.list(query, "ADMIN", user); }

    @GetMapping("/orders/{id}")
    public OrderService.Detail detail(@PathVariable long id, @AuthenticationPrincipal UserPrincipal user) { return orders.detail(id, "ADMIN", user); }
}
