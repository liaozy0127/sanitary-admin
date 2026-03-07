package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Shipment;
import com.sanitary.admin.mapper.ShipmentMapper;
import com.sanitary.admin.service.ShipmentService;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl extends ServiceImpl<ShipmentMapper, Shipment> implements ShipmentService {

    private final GenerateNoUtil generateNoUtil;

    @Override
    public Page<Shipment> pageList(int page, int size, String keyword, Long customerId,
                                   String startDate, String endDate) {
        LambdaQueryWrapper<Shipment> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Shipment::getShipmentNo, keyword)
                    .or().like(Shipment::getCustomerName, keyword)
                    .or().like(Shipment::getMaterialName, keyword));
        }
        if (customerId != null) {
            wrapper.eq(Shipment::getCustomerId, customerId);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(Shipment::getShipmentDate, LocalDate.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(Shipment::getShipmentDate, LocalDate.parse(endDate));
        }
        wrapper.orderByDesc(Shipment::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Shipment createShipment(Shipment shipment) {
        shipment.setShipmentNo(generateNoUtil.generate("FH", "shipment", "shipment_no"));
        if (shipment.getStatus() == null) {
            shipment.setStatus(1);
        }
        if (shipment.getQuantity() != null && shipment.getUnitPrice() != null) {
            shipment.setAmount(shipment.getQuantity().multiply(shipment.getUnitPrice()));
        }
        save(shipment);
        return shipment;
    }
}
