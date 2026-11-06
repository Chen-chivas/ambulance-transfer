package com.ambtransfer.dao.db.company;

import com.ambtransfer.entity.CarCompany;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CompanyMapper {
    String EFFECTIVE_STATUS = "CASE WHEN audit_status = 'APPROVED' AND (license_expire_at IS NULL OR license_expire_at < #{today}) THEN 'EXPIRED' ELSE audit_status END";
    String FILTER = " FROM dbo.car_company WHERE (#{keyword} = '' OR CHARINDEX(#{keyword}, name) > 0 OR CHARINDEX(#{keyword}, license_no) > 0) AND (#{status} = '' OR " + EFFECTIVE_STATUS + " = #{status})";

    @Select("SELECT id, name, license_no, license_expire_at, " + EFFECTIVE_STATUS + " AS audit_status, audit_remark, contact_phone, created_at, updated_at" + FILTER + " ORDER BY id DESC OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY")
    List<CarCompany> list(@Param("keyword") String keyword, @Param("status") String status, @Param("today") LocalDate today, @Param("offset") long offset, @Param("size") int size);

    @Select("SELECT COUNT_BIG(*)" + FILTER)
    long count(@Param("keyword") String keyword, @Param("status") String status, @Param("today") LocalDate today);

    @Select("SELECT * FROM dbo.car_company WHERE id = #{id}")
    CarCompany find(long id);

    @Select("SELECT * FROM dbo.car_company WITH (UPDLOCK, HOLDLOCK) WHERE id = #{id}")
    CarCompany lock(long id);

    @Insert("INSERT INTO dbo.car_company(name, license_no, license_expire_at, contact_phone) VALUES(#{name}, #{licenseNo}, #{licenseExpireAt}, #{contactPhone})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CarCompany company);

    @Update("UPDATE dbo.car_company SET name = #{name}, license_no = #{licenseNo}, license_expire_at = #{licenseExpireAt}, contact_phone = #{contactPhone}, audit_status = 'PENDING', audit_remark = NULL, updated_at = SYSUTCDATETIME() WHERE id = #{id}")
    int update(CarCompany company);

    @Update("UPDATE dbo.car_company SET audit_status = #{status}, audit_remark = #{remark}, updated_at = SYSUTCDATETIME() WHERE id = #{id} AND audit_status = 'PENDING'")
    int audit(@Param("id") long id, @Param("status") String status, @Param("remark") String remark);

    @Delete("DELETE FROM dbo.car_company WHERE id = #{id}")
    int delete(long id);
}
