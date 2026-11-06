package com.ambtransfer.dao.db.order;

import com.ambtransfer.dto.request.OrderQuery;
import com.ambtransfer.entity.TransferOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OrderMapper {
    String ASSIGNMENT_COLUMNS = "v.plate_no AS assigned_plate_no, ds.name AS assigned_driver_name, ns.name AS assigned_nurse_name";
    String ASSIGNMENT_JOINS = " LEFT JOIN dbo.vehicle v ON v.id=o.assigned_vehicle_id LEFT JOIN dbo.staff ds ON ds.id=o.assigned_driver_id LEFT JOIN dbo.staff ns ON ns.id=o.assigned_nurse_id ";
    String ELIGIBLE = "EXISTS (SELECT 1 FROM dbo.car_company ec WHERE ec.id = #{companyId} AND ec.audit_status = 'APPROVED' AND ec.license_expire_at >= #{today} AND EXISTS (SELECT 1 FROM dbo.vehicle ev WHERE ev.company_id = ec.id AND ev.service_level = o.service_level AND ev.status = 'IDLE'))";
    String FILTER = """
        WHERE (#{q.keyword} = '' OR CHARINDEX(#{q.keyword}, o.order_no) > 0 OR CHARINDEX(#{q.keyword}, o.origin_address) > 0 OR CHARINDEX(#{q.keyword}, o.dest_address) > 0)
          AND (#{q.serviceLevel} = '' OR o.service_level = #{q.serviceLevel})
          AND (#{q.status} = '' OR o.status = #{q.status})
        <choose>
          <when test="view == 'ASSIGNED'">AND o.assigned_company_id = #{companyId}</when>
          <when test="view == 'DRIVER'">AND o.assigned_driver_id = #{ownerId} AND o.assigned_company_id = #{companyId}</when>
          <when test="view == 'MOCK'">AND EXISTS (SELECT 1 FROM dbo.order_status_log l WHERE l.order_id = o.id AND l.from_status IS NULL AND l.operator_type = 'MOCK_USER' AND l.operator_id = #{ownerId})</when>
          <when test="view == 'MINE'">AND EXISTS (SELECT 1 FROM dbo.quote q WHERE q.order_id = o.id AND q.company_id = #{companyId})</when>
          <when test="view == 'POOL'">AND o.status IN ('PENDING_QUOTE', 'QUOTING') AND
        """ + ELIGIBLE + "</when></choose>";

    @Select("<script>SELECT o.*, " + ASSIGNMENT_COLUMNS + ", c.name AS assigned_company_name, mq.id AS my_quote_id, mq.amount AS my_amount, mq.status AS my_quote_status FROM dbo.transfer_order o LEFT JOIN dbo.car_company c ON c.id = o.assigned_company_id LEFT JOIN dbo.quote mq ON mq.order_id = o.id AND mq.company_id = #{companyId} " + ASSIGNMENT_JOINS + FILTER + " ORDER BY o.id DESC OFFSET #{q.offset} ROWS FETCH NEXT #{q.size} ROWS ONLY</script>")
    List<TransferOrder> list(@Param("q") OrderQuery query, @Param("view") String view, @Param("ownerId") Long ownerId, @Param("companyId") Long companyId, @Param("today") LocalDate today);

    @Select("<script>SELECT COUNT_BIG(*) FROM dbo.transfer_order o " + FILTER + "</script>")
    long count(@Param("q") OrderQuery query, @Param("view") String view, @Param("ownerId") Long ownerId, @Param("companyId") Long companyId, @Param("today") LocalDate today);

    @Select("SELECT o.*, " + ASSIGNMENT_COLUMNS + ", c.name AS assigned_company_name FROM dbo.transfer_order o LEFT JOIN dbo.car_company c ON c.id = o.assigned_company_id " + ASSIGNMENT_JOINS + " WHERE o.id = #{id}")
    TransferOrder find(long id);

    @Select("SELECT * FROM dbo.transfer_order WITH (UPDLOCK, HOLDLOCK) WHERE id = #{id}")
    TransferOrder lock(long id);

    @Select("SELECT COUNT(*) FROM dbo.order_status_log WHERE order_id = #{id} AND from_status IS NULL AND operator_type = 'MOCK_USER' AND operator_id = #{ownerId}")
    int owns(@Param("id") long id, @Param("ownerId") long ownerId);

    @Select("SELECT COUNT(*) FROM dbo.transfer_order o WHERE o.id = #{id} AND o.status IN ('PENDING_QUOTE', 'QUOTING') AND " + ELIGIBLE)
    int visibleInPool(@Param("id") long id, @Param("companyId") long companyId, @Param("today") LocalDate today);

    @Insert("INSERT INTO dbo.transfer_order(order_no, user_name, user_phone, origin_address, dest_address, service_level, patient_note) VALUES(#{orderNo}, #{userName}, #{userPhone}, #{originAddress}, #{destAddress}, #{serviceLevel}, #{patientNote})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TransferOrder order);

    @Update("UPDATE dbo.transfer_order SET status = 'QUOTING', updated_at = SYSUTCDATETIME() WHERE id = #{id} AND status = 'PENDING_QUOTE'")
    int startQuoting(long id);

    @Update("UPDATE dbo.transfer_order SET status = 'CONFIRMED', assigned_company_id = #{companyId}, final_amount = #{amount}, updated_at = SYSUTCDATETIME() WHERE id = #{id} AND status = 'QUOTING'")
    int confirm(@Param("id") long id, @Param("companyId") long companyId, @Param("amount") BigDecimal amount);

    @Update("UPDATE dbo.transfer_order SET status='DISPATCHED', assigned_vehicle_id=#{vehicleId}, assigned_driver_id=#{driverId}, assigned_nurse_id=#{nurseId,jdbcType=BIGINT}, updated_at=SYSUTCDATETIME() WHERE id=#{id} AND assigned_company_id=#{companyId} AND status='CONFIRMED'")
    int dispatch(@Param("id") long id, @Param("companyId") long companyId, @Param("vehicleId") long vehicleId, @Param("driverId") long driverId, @Param("nurseId") Long nurseId);

    @Update("UPDATE dbo.transfer_order SET status=#{to}, updated_at=SYSUTCDATETIME() WHERE id=#{id} AND assigned_driver_id=#{driverId} AND assigned_company_id=#{companyId} AND status=#{from}")
    int advance(@Param("id") long id, @Param("companyId") long companyId, @Param("driverId") long driverId, @Param("from") String from, @Param("to") String to);
}
