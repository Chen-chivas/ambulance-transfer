package com.ambtransfer.dao.db.order;

import com.ambtransfer.entity.OrderStatusLog;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderLogMapper {
    @Insert("INSERT INTO dbo.order_status_log(order_id, from_status, to_status, operator_type, operator_id, event_type, remark) VALUES(#{orderId}, #{fromStatus}, #{toStatus}, #{operatorType}, #{operatorId}, #{eventType}, #{remark})")
    int insert(OrderStatusLog log);

    @Select("SELECT * FROM dbo.order_status_log WHERE order_id = #{orderId} AND (#{onlyStatus} = 0 OR event_type = 'STATUS_CHANGE') ORDER BY created_at, id")
    List<OrderStatusLog> list(@Param("orderId") long orderId, @Param("onlyStatus") boolean onlyStatus);
}
