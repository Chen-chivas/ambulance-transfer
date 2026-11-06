package com.ambtransfer.service.auth;

import com.ambtransfer.dao.db.staff.StaffMapper;
import com.ambtransfer.entity.Staff;
import java.nio.charset.StandardCharsets;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final StaffMapper staff;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transaction;
    private final String username;
    private final String password;

    public AdminBootstrap(StaffMapper staff, PasswordEncoder passwords, TransactionTemplate transaction,
                          @Value("${app.bootstrap.username:}") String username,
                          @Value("${app.bootstrap.password:}") String password) {
        this.staff = staff; this.passwords = passwords; this.transaction = transaction;
        this.username = username; this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() && password.isBlank()) return;
        if (!username.matches("[A-Za-z0-9_.-]{3,50}") || password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("初始化管理员需要 3–50 位英文登录名和至少 10 个字符、至多 72 字节的密码");
        }
        String hash = passwords.encode(password);
        transaction.executeWithoutResult(status -> {
            if (staff.countAdminsForBootstrap() > 0) {
                LoggerFactory.getLogger(AdminBootstrap.class).info("管理员已存在，跳过初始化。");
                return;
            }
            var account = new Staff();
            account.name = "平台管理员"; account.role = "ADMIN"; account.phone = "未设置";
            account.username = username; account.passwordHash = hash;
            staff.insert(account);
            LoggerFactory.getLogger(AdminBootstrap.class).info("首位管理员已创建。");
        });
    }
}
