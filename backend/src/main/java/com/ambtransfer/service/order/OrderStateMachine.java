package com.ambtransfer.service.order;

import com.ambtransfer.enums.OrderStatus;
import com.ambtransfer.exception.BusinessException;
import java.util.Map;
import java.util.Set;
import static com.ambtransfer.enums.OrderStatus.*;

public final class OrderStateMachine {
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING_QUOTE, Set.of(QUOTING, CANCELLED),
            QUOTING, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(DISPATCHED, CANCELLED),
            DISPATCHED, Set.of(EN_ROUTE, CANCELLED),
            EN_ROUTE, Set.of(ARRIVED, CANCELLED),
            ARRIVED, Set.of(ON_BOARD, CANCELLED),
            ON_BOARD, Set.of(COMPLETED),
            COMPLETED, Set.of(),
            CANCELLED, Set.of());

    private OrderStateMachine() {
    }

    public static void validate(OrderStatus from, OrderStatus to) {
        if (from == null || to == null || !TRANSITIONS.get(from).contains(to)) {
            throw new BusinessException("不允许订单状态从 " + from + " 变更为 " + to);
        }
    }
}
