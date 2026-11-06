package com.ambtransfer.controller.admin;

import com.ambtransfer.dto.request.StaffRequests;
import com.ambtransfer.dto.response.*;
import com.ambtransfer.service.company.StaffService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/companies/{companyId}/staff")
public class AdminStaffController {
    private final StaffService staff;
    public AdminStaffController(StaffService staff) { this.staff = staff; }
    @GetMapping
    public PageResult<StaffSummary> list(@PathVariable long companyId,
        @RequestParam(defaultValue="") @Size(max=100) String keyword,
        @RequestParam(defaultValue="") @Pattern(regexp="|DRIVER|NURSE") String role,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,
        @RequestParam(defaultValue="10") @Min(1) @Max(100) int size) { return staff.list(companyId, keyword, role, page, size); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public StaffSummary create(@PathVariable long companyId, @Valid @RequestBody StaffRequests.Create request) { return staff.create(companyId, request); }
    @PutMapping("/{id}")
    public StaffSummary update(@PathVariable long companyId, @PathVariable long id, @Valid @RequestBody StaffRequests.Update request) { return staff.update(companyId, id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long companyId, @PathVariable long id) { staff.delete(companyId, id); }
}
