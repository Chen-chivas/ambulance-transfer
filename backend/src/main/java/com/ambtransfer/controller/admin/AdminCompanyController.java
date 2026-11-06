package com.ambtransfer.controller.admin;

import com.ambtransfer.dto.request.Requests;
import com.ambtransfer.dto.response.PageResult;
import com.ambtransfer.entity.CarCompany;
import com.ambtransfer.security.UserPrincipal;
import com.ambtransfer.service.admin.CompanyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/companies")
public class AdminCompanyController {
    private final CompanyService companies;
    public AdminCompanyController(CompanyService companies) { this.companies = companies; }

    @GetMapping
    public PageResult<CarCompany> list(@RequestParam(defaultValue = "") @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "") @Pattern(regexp = "|PENDING|APPROVED|REJECTED|EXPIRED") String status,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return companies.list(keyword, status, page, size);
    }

    @GetMapping("/{id}")
    public CarCompany find(@PathVariable long id) { return companies.find(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CarCompany create(@Valid @RequestBody Requests.Company request) { return companies.save(null, request); }

    @PutMapping("/{id}")
    public CarCompany update(@PathVariable long id, @Valid @RequestBody Requests.Company request) { return companies.save(id, request); }

    @PostMapping("/{id}/audit")
    public CarCompany audit(@PathVariable long id, @Valid @RequestBody Requests.Audit request) { return companies.audit(id, request); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) { companies.delete(id); }

    @GetMapping("/{id}/dispatchers")
    public List<UserPrincipal> dispatchers(@PathVariable long id) { return companies.dispatchers(id); }

    @PostMapping("/{id}/dispatchers")
    @ResponseStatus(HttpStatus.CREATED)
    public UserPrincipal createDispatcher(@PathVariable long id, @Valid @RequestBody Requests.Dispatcher request) { return companies.createDispatcher(id, request); }
}
