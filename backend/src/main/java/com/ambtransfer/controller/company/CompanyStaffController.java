package com.ambtransfer.controller.company;

import com.ambtransfer.dto.request.StaffRequests;
import com.ambtransfer.dto.response.*;
import com.ambtransfer.security.UserPrincipal;
import com.ambtransfer.service.company.StaffService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/company/staff")
public class CompanyStaffController {
    private final StaffService staff;
    public CompanyStaffController(StaffService staff) { this.staff = staff; }
    @GetMapping
    public PageResult<StaffSummary> list(@AuthenticationPrincipal UserPrincipal user,
        @RequestParam(defaultValue="") @Size(max=100) String keyword,
        @RequestParam(defaultValue="") @Pattern(regexp="|DRIVER|NURSE") String role,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,
        @RequestParam(defaultValue="10") @Min(1) @Max(100) int size) { return staff.list(user.companyId(), keyword, role, page, size); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public StaffSummary create(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody StaffRequests.Create request) { return staff.create(user.companyId(), request); }
    @PutMapping("/{id}")
    public StaffSummary update(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id, @Valid @RequestBody StaffRequests.Update request) { return staff.update(user.companyId(), id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) { staff.delete(user.companyId(), id); }
}
