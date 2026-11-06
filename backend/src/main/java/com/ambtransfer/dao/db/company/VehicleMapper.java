package com.ambtransfer.dao.db.company;

import com.ambtransfer.entity.Vehicle;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface VehicleMapper {
    String FILTER = " FROM dbo.vehicle WHERE company_id = #{companyId} AND (#{keyword} = '' OR CHARINDEX(#{keyword}, plate_no) > 0) AND (#{status} = '' OR status = #{status})";

    @Select("SELECT *" + FILTER + " ORDER BY id DESC OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<Vehicle> list(@Param("companyId") long companyId, @Param("keyword") String keyword, @Param("status") String status, @Param("offset") long offset, @Param("size") int size);

    @Select("SELECT COUNT_BIG(*)" + FILTER)
    long count(@Param("companyId") long companyId, @Param("keyword") String keyword, @Param("status") String status);

    @Select("SELECT * FROM dbo.vehicle WITH (UPDLOCK, HOLDLOCK) WHERE id = #{id} AND company_id = #{companyId}")
    Vehicle lock(@Param("companyId") long companyId, @Param("id") long id);

    @Insert("INSERT INTO dbo.vehicle(company_id, plate_no, service_level, status) VALUES(#{companyId}, #{plateNo}, #{serviceLevel}, #{status})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Vehicle vehicle);

    @Update("UPDATE dbo.vehicle SET plate_no = #{plateNo}, service_level = #{serviceLevel}, status = #{status}, updated_at = SYSUTCDATETIME() WHERE id = #{id} AND company_id = #{companyId} AND status <> 'TASK'")
    int update(Vehicle vehicle);

    @Delete("DELETE FROM dbo.vehicle WHERE id = #{id} AND company_id = #{companyId} AND status <> 'TASK'")
    int delete(@Param("companyId") long companyId, @Param("id") long id);
}
