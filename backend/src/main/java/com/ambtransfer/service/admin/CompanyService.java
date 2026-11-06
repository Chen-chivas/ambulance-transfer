package com.ambtransfer.service.admin;

import com.ambtransfer.dao.db.company.CompanyMapper;
import com.ambtransfer.dao.db.staff.StaffMapper;
import com.ambtransfer.dto.request.Requests;
import com.ambtransfer.dto.response.PageResult;
import com.ambtransfer.entity.CarCompany;
import com.ambtransfer.entity.Staff;
import com.ambtransfer.exception.ApiException;
import com.ambtransfer.exception.BusinessException;
import com.ambtransfer.security.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {
    private final CompanyMapper companies;
    private final StaffMapper staff;
    private final PasswordEncoder passwords;

    public CompanyService(CompanyMapper companies, StaffMapper staff, PasswordEncoder passwords) {
        this.companies = companies; this.staff = staff; this.passwords = passwords;
    }

    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")); }

    public PageResult<CarCompany> list(String keyword, String status, int page, int size) {
        LocalDate today = today();
        return new PageResult<>(companies.list(keyword.trim(), status, today, (long) (page - 1) * size, size),
            companies.count(keyword.trim(), status, today), page, size);
    }

    public CarCompany find(long id) {
        var company = companies.find(id);
        if (company == null) throw ApiException.notFound();
        if ("APPROVED".equals(company.auditStatus) && (company.licenseExpireAt == null || company.licenseExpireAt.isBefore(today()))) company.auditStatus = "EXPIRED";
        return company;
    }

    @Transactional
    public CarCompany save(Long id, Requests.Company request) {
        if (id != null && companies.lock(id) == null) throw ApiException.notFound();
        var company = new CarCompany();
        company.id = id; company.name = request.name().trim(); company.licenseNo = request.licenseNo().trim();
        company.licenseExpireAt = request.licenseExpireAt(); company.contactPhone = request.contactPhone().trim();
        if (id == null) companies.insert(company); else companies.update(company);
        return find(company.id);
    }

    @Transactional
    public CarCompany audit(long id, Requests.Audit request) {
        var company = companies.lock(id);
        if (company == null) throw ApiException.notFound();
        if (!"PENDING".equals(company.auditStatus)) throw new BusinessException("该车行已处理，请刷新；修改资料后可重新送审");
        String remark = request.remark() == null ? null : request.remark().trim();
        if ("REJECTED".equals(request.status()) && (remark == null || remark.isEmpty())) throw new BusinessException("请填写驳回原因");
        if ("APPROVED".equals(request.status()) && (company.licenseExpireAt == null || company.licenseExpireAt.isBefore(today()))) throw new BusinessException("通过审核前，请补充有效的资质到期日");
        if (companies.audit(id, request.status(), remark) != 1) throw new BusinessException("审核状态已变化，请刷新");
        return find(id);
    }

    @Transactional
    public void delete(long id) {
        if (companies.lock(id) == null) throw ApiException.notFound();
        companies.delete(id);
    }

    public List<UserPrincipal> dispatchers(long id) {
        find(id);
        return staff.dispatchers(id).stream().map(UserPrincipal::from).toList();
    }

    @Transactional
    public UserPrincipal createDispatcher(long id, Requests.Dispatcher request) {
        if (companies.lock(id) == null) throw ApiException.notFound();
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new BusinessException("密码不能超过 72 字节");
        var account = new Staff();
        account.companyId = id; account.name = request.name().trim(); account.role = "DISPATCHER";
        account.phone = request.phone().trim(); account.username = request.username();
        account.passwordHash = passwords.encode(request.password());
        staff.insert(account);
        return UserPrincipal.from(account);
    }
}
