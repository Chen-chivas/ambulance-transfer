package com.ambtransfer.controller.admin;

import com.ambtransfer.dao.db.system.OverviewMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/overview")
public class AdminOverviewController {
    private final OverviewMapper mapper;
    public AdminOverviewController(OverviewMapper mapper) { this.mapper = mapper; }

    @GetMapping
    public Map<String, Long> overview() { return mapper.overview(LocalDate.now(ZoneId.of("Asia/Shanghai"))); }
}
