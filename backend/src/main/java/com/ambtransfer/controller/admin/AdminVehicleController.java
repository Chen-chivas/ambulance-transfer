package com.ambtransfer.controller.admin;

import com.ambtransfer.dto.request.Requests;
import com.ambtransfer.dto.response.PageResult;
import com.ambtransfer.entity.Vehicle;
import com.ambtransfer.service.company.VehicleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/companies/{companyId}/vehicles")
public class AdminVehicleController {
    private final VehicleService vehicles;
    public AdminVehicleController(VehicleService vehicles) { this.vehicles = vehicles; }

    @GetMapping
    public PageResult<Vehicle> list(@PathVariable long companyId,
            @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "") @Pattern(regexp = "|IDLE|TASK|MAINTENANCE") String status,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return vehicles.list(companyId, keyword, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Vehicle create(@PathVariable long companyId, @Valid @RequestBody Requests.VehicleInput request) { return vehicles.save(companyId, null, request); }

    @PutMapping("/{id}")
    public Vehicle update(@PathVariable long companyId, @PathVariable long id, @Valid @RequestBody Requests.VehicleInput request) { return vehicles.save(companyId, id, request); }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long companyId, @PathVariable long id) { vehicles.delete(companyId, id); }
}
