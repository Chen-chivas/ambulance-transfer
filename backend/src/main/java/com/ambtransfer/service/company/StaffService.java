package com.ambtransfer.service.company;

import com.ambtransfer.dao.db.company.CompanyMapper;
import com.ambtransfer.dao.db.staff.StaffMapper;
import com.ambtransfer.dto.request.StaffRequests;
import com.ambtransfer.dto.response.*;
import com.ambtransfer.entity.Staff;
import com.ambtransfer.exception.*;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {
    private final StaffMapper staff;
    private final CompanyMapper companies;
    private final PasswordEncoder passwords;
    public StaffService(StaffMapper staff, CompanyMapper companies, PasswordEncoder passwords) { this.staff = staff; this.companies = companies; this.passwords = passwords; }

    public PageResult<StaffSummary> list(long companyId, String keyword, String role, int page, int size) {
        if (companies.find(companyId) == null) throw ApiException.notFound();
        return new PageResult<>(staff.list(companyId, keyword.trim(), role, (long)(page-1)*size, size).stream().map(StaffSummary::from).toList(), staff.count(companyId, keyword.trim(), role), page, size);
    }

    @Transactional
    public StaffSummary create(long companyId, StaffRequests.Create request) {
        if (companies.lock(companyId) == null) throw ApiException.notFound();
        var value = new Staff();
        value.companyId = companyId; value.name = request.name().trim(); value.phone = request.phone().trim(); value.role = request.role();
        value.certNo = request.certNo().trim(); value.certExpireAt = request.certExpireAt();
        if ("DRIVER".equals(value.role)) {
            if (request.username() == null || !request.username().matches("[A-Za-z0-9_.-]{3,50}") || request.password() == null || request.password().length() < 10 || request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
                throw new BusinessException("司机需要 3–50 位英文登录名和至少 10 字符、最多 72 字节的密码");
            }
            value.username = request.username(); value.passwordHash = passwords.encode(request.password());
        } else if (request.username() != null && !request.username().isBlank() || request.password() != null && !request.password().isBlank()) {
            throw new BusinessException("护士仅维护人员资料，无需登录账号");
        }
        staff.insert(value); value.enabled = true;
        return StaffSummary.from(value);
    }

    @Transactional
    public StaffSummary update(long companyId, long id, StaffRequests.Update request) {
        if (companies.lock(companyId) == null) throw ApiException.notFound();
        var value = editable(companyId, id);
        value.name = request.name().trim(); value.phone = request.phone().trim(); value.certNo = request.certNo().trim();
        value.certExpireAt = request.certExpireAt(); value.enabled = request.enabled();
        staff.update(value); return StaffSummary.from(value);
    }

    private Staff editable(long companyId, long id) {
        var value = staff.lock(companyId, id);
        if (value == null) throw ApiException.notFound();
        if (staff.activeTasks(id) > 0) throw new BusinessException("执行任务中的人员不能修改、停用或删除");
        return value;
    }

    @Transactional
    public void delete(long companyId, long id) {
        if (companies.lock(companyId) == null) throw ApiException.notFound();
        editable(companyId, id); staff.delete(companyId, id);
    }
}
