package com.ambtransfer.controller.driver;

import com.ambtransfer.dto.request.OrderQuery;
import com.ambtransfer.dto.response.PageResult;
import com.ambtransfer.entity.TransferOrder;
import com.ambtransfer.security.UserPrincipal;
import com.ambtransfer.service.order.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/driver/tasks")
public class DriverTaskController {
    private final OrderService orders;
    private final DispatchService dispatch;
    public DriverTaskController(OrderService orders, DispatchService dispatch) { this.orders=orders; this.dispatch=dispatch; }
    @GetMapping
    public PageResult<TransferOrder> list(@Valid @ModelAttribute OrderQuery query, @AuthenticationPrincipal UserPrincipal user) { return orders.list(query, "DRIVER", user); }
    @GetMapping("/{id}")
    public OrderService.Detail detail(@PathVariable long id, @AuthenticationPrincipal UserPrincipal user) { return orders.detail(id, "DRIVER", user); }
    @PostMapping("/{id}/{action:depart|arrive|pickup|complete}")
    public TransferOrder advance(@PathVariable long id, @PathVariable String action, @AuthenticationPrincipal UserPrincipal user) { return dispatch.advance(id, action, user); }
}
