package com.ambtransfer.controller.company;

import com.ambtransfer.dto.request.*;
import com.ambtransfer.dto.response.PageResult;
import com.ambtransfer.entity.*;
import com.ambtransfer.security.UserPrincipal;
import com.ambtransfer.service.order.OrderService;
import com.ambtransfer.service.order.DispatchService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/company/orders")
public class CompanyOrderController {
    private final OrderService orders;
    private final DispatchService dispatch;
    public CompanyOrderController(OrderService orders, DispatchService dispatch) { this.orders = orders; this.dispatch = dispatch; }

    @GetMapping("/assigned")
    public PageResult<TransferOrder> assigned(@Valid @ModelAttribute OrderQuery query, @AuthenticationPrincipal UserPrincipal user) { return orders.list(query, "ASSIGNED", user); }

    @GetMapping("/{id}/resources")
    public DispatchService.Resources resources(@PathVariable long id, @AuthenticationPrincipal UserPrincipal user) { return dispatch.resources(id, user); }

    @PostMapping("/{id}/dispatch")
    public TransferOrder dispatch(@PathVariable long id, @Valid @RequestBody DispatchRequest request, @AuthenticationPrincipal UserPrincipal user) { return dispatch.dispatch(id, request, user); }

    @GetMapping("/pool")
    public PageResult<TransferOrder> pool(@Valid @ModelAttribute OrderQuery query, @AuthenticationPrincipal UserPrincipal user) { return orders.list(query, "POOL", user); }

    @GetMapping("/mine")
    public PageResult<TransferOrder> mine(@Valid @ModelAttribute OrderQuery query, @AuthenticationPrincipal UserPrincipal user) { return orders.list(query, "MINE", user); }

    @GetMapping("/{id}")
    public OrderService.Detail detail(@PathVariable long id, @AuthenticationPrincipal UserPrincipal user) { return orders.detail(id, "COMPANY", user); }

    @PostMapping("/{id}/quotes")
    public Quote bid(@PathVariable long id, @Valid @RequestBody OrderRequests.Bid request, @AuthenticationPrincipal UserPrincipal user) { return orders.bid(id, request, user); }
}
