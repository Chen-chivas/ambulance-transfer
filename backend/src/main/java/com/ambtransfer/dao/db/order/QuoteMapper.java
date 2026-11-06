package com.ambtransfer.dao.db.order;

import com.ambtransfer.entity.Quote;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface QuoteMapper {
    @Select("SELECT * FROM dbo.quote WHERE order_id = #{orderId} AND company_id = #{companyId}")
    Quote own(@Param("orderId") long orderId, @Param("companyId") long companyId);

    @Select("SELECT * FROM dbo.quote WHERE id = #{id} AND order_id = #{orderId}")
    Quote find(@Param("orderId") long orderId, @Param("id") long id);

    @Select("SELECT q.*, c.name AS company_name, CAST(CASE WHEN c.audit_status = 'APPROVED' AND c.license_expire_at >= #{today} AND EXISTS (SELECT 1 FROM dbo.vehicle v JOIN dbo.transfer_order o ON o.id = q.order_id WHERE v.company_id = c.id AND v.service_level = o.service_level AND v.status = 'IDLE') THEN 1 ELSE 0 END AS BIT) AS eligible FROM dbo.quote q JOIN dbo.car_company c ON c.id = q.company_id WHERE q.order_id = #{orderId} AND (#{companyId} IS NULL OR q.company_id = #{companyId}) ORDER BY q.amount, q.id")
    List<Quote> list(@Param("orderId") long orderId, @Param("companyId") Long companyId, @Param("today") LocalDate today);

    @Insert("INSERT INTO dbo.quote(order_id, company_id, amount, remark) VALUES(#{orderId}, #{companyId}, #{amount}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Quote quote);

    @Update("UPDATE dbo.quote SET amount = #{amount}, remark = #{remark}, updated_at = SYSUTCDATETIME() WHERE id = #{id} AND status = 'PENDING'")
    int update(Quote quote);

    @Update("UPDATE dbo.quote SET status = CASE WHEN id = #{winnerId} THEN 'WON' ELSE 'LOST' END, updated_at = SYSUTCDATETIME() WHERE order_id = #{orderId} AND status = 'PENDING'")
    int settle(@Param("orderId") long orderId, @Param("winnerId") long winnerId);
}
