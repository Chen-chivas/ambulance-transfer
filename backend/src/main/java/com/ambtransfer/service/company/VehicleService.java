package com.ambtransfer.service.company;

import com.ambtransfer.dao.db.company.CompanyMapper;
import com.ambtransfer.dao.db.company.VehicleMapper;
import com.ambtransfer.dto.request.Requests;
import com.ambtransfer.dto.response.PageResult;
import com.ambtransfer.entity.Vehicle;
import com.ambtransfer.exception.ApiException;
import com.ambtransfer.exception.BusinessException;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {
    private final VehicleMapper vehicles;
    private final CompanyMapper companies;

    public VehicleService(VehicleMapper vehicles, CompanyMapper companies) { this.vehicles = vehicles; this.companies = companies; }

    public PageResult<Vehicle> list(long companyId, String keyword, String status, int page, int size) {
        if (companies.find(companyId) == null) throw ApiException.notFound();
        return new PageResult<>(vehicles.list(companyId, keyword.trim(), status, (long) (page - 1) * size, size),
            vehicles.count(companyId, keyword.trim(), status), page, size);
    }

    @Transactional
    public Vehicle save(long companyId, Long id, Requests.VehicleInput request) {
        if (companies.lock(companyId) == null) throw ApiException.notFound();
        if (id != null) ensureEditable(companyId, id);
        var vehicle = new Vehicle();
        vehicle.id = id; vehicle.companyId = companyId; vehicle.plateNo = request.plateNo().trim().toUpperCase(Locale.ROOT);
        vehicle.serviceLevel = request.serviceLevel(); vehicle.status = request.status();
        if (id == null) vehicles.insert(vehicle);
        else if (vehicles.update(vehicle) != 1) throw new BusinessException("车辆状态已变化，请刷新");
        return vehicles.lock(companyId, vehicle.id);
    }

    private void ensureEditable(long companyId, long id) {
        var vehicle = vehicles.lock(companyId, id);
        if (vehicle == null) throw ApiException.notFound();
        if ("TASK".equals(vehicle.status)) throw new BusinessException("执行任务中的车辆不能修改或删除");
    }

    @Transactional
    public void delete(long companyId, long id) {
        if (companies.lock(companyId) == null) throw ApiException.notFound();
        ensureEditable(companyId, id);
        if (vehicles.delete(companyId, id) != 1) throw new BusinessException("车辆状态已变化，请刷新");
    }
}
