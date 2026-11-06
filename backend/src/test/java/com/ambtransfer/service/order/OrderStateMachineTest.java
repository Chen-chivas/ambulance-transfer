package com.ambtransfer.service.order;

import com.ambtransfer.enums.OrderStatus;
import com.ambtransfer.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static com.ambtransfer.enums.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderStateMachineTest {
    @Test
    void acceptsCompleteTransferLifecycle() {
        OrderStatus[] lifecycle = {PENDING_QUOTE, QUOTING, CONFIRMED, DISPATCHED, EN_ROUTE, ARRIVED, ON_BOARD, COMPLETED};
        for (int index = 0; index < lifecycle.length - 1; index++) {
            OrderStatus from = lifecycle[index];
            OrderStatus to = lifecycle[index + 1];
            assertDoesNotThrow(() -> OrderStateMachine.validate(from, to));
        }
    }

    @Test
    void rejectsSkippingStepsAndChangingTerminalStates() {
        assertThrows(BusinessException.class, () -> OrderStateMachine.validate(PENDING_QUOTE, COMPLETED));
        for (OrderStatus target : OrderStatus.values()) {
            assertThrows(BusinessException.class, () -> OrderStateMachine.validate(COMPLETED, target));
            assertThrows(BusinessException.class, () -> OrderStateMachine.validate(CANCELLED, target));
        }
    }

    @Test
    void onlyAllowsCancellationBeforeBoarding() {
        assertDoesNotThrow(() -> OrderStateMachine.validate(ARRIVED, CANCELLED));
        assertThrows(BusinessException.class, () -> OrderStateMachine.validate(ON_BOARD, CANCELLED));
        assertThrows(BusinessException.class, () -> OrderStateMachine.validate(null, CANCELLED));
        assertThrows(BusinessException.class, () -> OrderStateMachine.validate(QUOTING, null));
    }
}
