package com.ambtransfer.controller;

import com.ambtransfer.dao.db.system.DatabaseHealthMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
    private static final Logger LOG = LoggerFactory.getLogger(HealthController.class);
    private final DatabaseHealthMapper databaseHealthMapper;

    public HealthController(DatabaseHealthMapper databaseHealthMapper) {
        this.databaseHealthMapper = databaseHealthMapper;
    }

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/db")
    public ResponseEntity<Map<String, String>> databaseHealth() {
        try {
            String database = databaseHealthMapper.databaseName();
            if (!"ambulance_transfer".equalsIgnoreCase(database)) {
                return ResponseEntity.status(503).body(Map.of(
                        "status", "error", "message", "数据库名称不匹配，请检查 DB_URL"));
            }
            return ResponseEntity.ok(Map.of("status", "ok", "database", database));
        } catch (DataAccessException exception) {
            LOG.warn("SQL Server health check failed: {}", exception.getClass().getSimpleName());
            return ResponseEntity.status(503).body(Map.of(
                    "status", "error", "message", "SQL Server 连接失败，请检查连接配置及数据库服务"));
        }
    }
}
