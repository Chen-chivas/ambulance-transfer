package com.ambtransfer.controller.company;

import com.ambtransfer.dto.request.Requests;
import com.ambtransfer.dto.response.PageResult;
import com.ambtransfer.entity.CarCompany;
import com.ambtransfer.entity.Vehicle;
import com.ambtransfer.security.UserPrincipal;
import com.ambtransfer.service.admin.CompanyService;
import com.ambtransfer.service.company.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/company")
public class CompanyVehicleController {
    private final VehicleService vehicles;
    private final CompanyService companies;
    public CompanyVehicleController(VehicleService vehicles, CompanyService companies) { this.vehicles = vehicles; this.companies = companies; }

    @GetMapping("/profile")
    public CarCompany profile(@AuthenticationPrincipal UserPrincipal user) { return companies.find(user.companyId()); }

    @GetMapping("/vehicles")
    public PageResult<Vehicle> list(@AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "") @Pattern(regexp = "|IDLE|TASK|MAINTENANCE") String status,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return vehicles.list(user.companyId(), keyword, status, page, size);
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    public Vehicle create(@AuthenticationPrincipal UserPrincipal user, @Valid @RequestBody Requests.VehicleInput request) { return vehicles.save(user.companyId(), null, request); }

    @PutMapping("/vehicles/{id}")
    public Vehicle update(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id, @Valid @RequestBody Requests.VehicleInput request) { return vehicles.save(user.companyId(), id, request); }

    @DeleteMapping("/vehicles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal user, @PathVariable long id) { vehicles.delete(user.companyId(), id); }
}
