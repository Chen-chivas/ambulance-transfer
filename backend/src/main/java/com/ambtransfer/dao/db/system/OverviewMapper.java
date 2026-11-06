package com.ambtransfer.dao.db.system;

import java.time.LocalDate;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OverviewMapper {
    @Select("""
        SELECT (SELECT COUNT_BIG(*) FROM dbo.car_company) AS companies,
          (SELECT COUNT_BIG(*) FROM dbo.car_company WHERE audit_status = 'PENDING') AS pending,
          (SELECT COUNT_BIG(*) FROM dbo.car_company WHERE audit_status = 'APPROVED' AND license_expire_at >= #{today}) AS approved,
          (SELECT COUNT_BIG(*) FROM dbo.vehicle) AS vehicles,
          (SELECT COUNT_BIG(*) FROM dbo.vehicle WHERE status = 'IDLE') AS idleVehicles
        """)
    Map<String, Long> overview(LocalDate today);
}
