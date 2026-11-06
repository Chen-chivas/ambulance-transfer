package com.ambtransfer.controller.mock;

import com.ambtransfer.dto.request.*;
import com.ambtransfer.dto.response.PageResult;
import com.ambtransfer.entity.TransferOrder;
import com.ambtransfer.exception.ApiException;
import com.ambtransfer.security.UserPrincipal;
import com.ambtransfer.service.order.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mock/orders")
public class MockOrderController {
    private final OrderService orders;
    private final boolean enabled;
    public MockOrderController(OrderService orders, @Value("${app.mock-orders.enabled:false}") boolean enabled) { this.orders = orders; this.enabled = enabled; }
    private void checkEnabled() { if (!enabled) throw new ApiException(404, "MOCK_DISABLED", "模拟下单未启用"); }

    @GetMapping
    public PageResult<TransferOrder> list(@Valid @ModelAttribute OrderQuery query, @AuthenticationPrincipal UserPrincipal user) {
        checkEnabled(); return orders.list(query, "MOCK", user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferOrder create(@Valid @RequestBody OrderRequests.Create request, @AuthenticationPrincipal UserPrincipal user) {
        checkEnabled(); return orders.create(request, user);
    }

    @GetMapping("/{id}")
    public OrderService.Detail detail(@PathVariable long id, @AuthenticationPrincipal UserPrincipal user) {
        checkEnabled(); return orders.detail(id, "MOCK", user);
    }

    @PostMapping("/{id}/select")
    public TransferOrder select(@PathVariable long id, @Valid @RequestBody OrderRequests.Select request, @AuthenticationPrincipal UserPrincipal user) {
        checkEnabled(); return orders.select(id, request, user);
    }
}
