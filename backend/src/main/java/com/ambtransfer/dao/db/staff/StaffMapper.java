package com.ambtransfer.dao.db.staff;

import com.ambtransfer.entity.Staff;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface StaffMapper {
    String ACTIVE = "('DISPATCHED','EN_ROUTE','ARRIVED','ON_BOARD')";
    String BUSY = "EXISTS (SELECT 1 FROM dbo.transfer_order o WHERE o.status IN " + ACTIVE + " AND (o.assigned_driver_id = s.id OR o.assigned_nurse_id = s.id))";
    String FILTER = " FROM dbo.staff s WHERE s.company_id = #{companyId} AND s.role IN ('DRIVER','NURSE') AND (#{keyword} = '' OR CHARINDEX(#{keyword}, s.name) > 0 OR CHARINDEX(#{keyword}, s.phone) > 0 OR CHARINDEX(#{keyword}, s.cert_no) > 0) AND (#{role} = '' OR s.role = #{role})";

    @Select("SELECT s.id, s.company_id, s.name, s.phone, s.role, s.username, s.enabled, s.cert_no, s.cert_expire_at, CAST(CASE WHEN " + BUSY + " THEN 1 ELSE 0 END AS BIT) AS busy" + FILTER + " ORDER BY s.id DESC OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<Staff> list(@Param("companyId") long companyId, @Param("keyword") String keyword, @Param("role") String role, @Param("offset") long offset, @Param("size") int size);

    @Select("SELECT COUNT_BIG(*)" + FILTER)
    long count(@Param("companyId") long companyId, @Param("keyword") String keyword, @Param("role") String role);

    @Select("SELECT * FROM dbo.staff WITH (UPDLOCK, HOLDLOCK) WHERE company_id = #{companyId} AND id = #{id} AND role IN ('DRIVER','NURSE')")
    Staff lock(@Param("companyId") long companyId, @Param("id") long id);

    @Select("SELECT COUNT(*) FROM dbo.transfer_order WHERE status IN " + ACTIVE + " AND (assigned_driver_id = #{id} OR assigned_nurse_id = #{id})")
    int activeTasks(long id);

    @Update("UPDATE dbo.staff SET name=#{name}, phone=#{phone}, cert_no=#{certNo}, cert_expire_at=#{certExpireAt}, enabled=#{enabled}, updated_at=SYSUTCDATETIME() WHERE id=#{id} AND company_id=#{companyId} AND role IN ('DRIVER','NURSE')")
    int update(Staff value);

    @Delete("DELETE FROM dbo.staff WHERE id=#{id} AND company_id=#{companyId} AND role IN ('DRIVER','NURSE')")
    int delete(@Param("companyId") long companyId, @Param("id") long id);

    @Select("SELECT s.id, s.company_id, s.name, s.phone, s.role, s.username, s.enabled, s.cert_no, s.cert_expire_at FROM dbo.staff s WHERE s.company_id=#{companyId} AND s.role IN ('DRIVER','NURSE') AND (s.role = 'NURSE' OR (s.username IS NOT NULL AND s.password_hash IS NOT NULL)) AND s.enabled=1 AND s.cert_no IS NOT NULL AND LEN(LTRIM(RTRIM(s.cert_no))) > 0 AND s.cert_expire_at >= #{today} AND NOT " + BUSY + " ORDER BY s.role, s.id")
    List<Staff> available(@Param("companyId") long companyId, @Param("today") java.time.LocalDate today);
    @Select("SELECT * FROM dbo.staff WHERE username = #{username}")
    Staff findByUsername(String username);

    @Select("SELECT * FROM dbo.staff WHERE id = #{id}")
    Staff findById(long id);

    @Select("SELECT id, company_id, name, role, username, phone, enabled FROM dbo.staff WHERE company_id = #{companyId} AND role = 'DISPATCHER' ORDER BY id DESC")
    List<Staff> dispatchers(long companyId);

    @Insert("INSERT INTO dbo.staff(company_id, name, role, phone, username, password_hash, cert_no, cert_expire_at) VALUES(#{companyId}, #{name}, #{role}, #{phone}, #{username}, #{passwordHash}, #{certNo}, #{certExpireAt,jdbcType=DATE})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Staff staff);

    @Select("SELECT COUNT(*) FROM dbo.staff WITH (UPDLOCK, HOLDLOCK) WHERE role = 'ADMIN'")
    long countAdminsForBootstrap();
}
