package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.entity.Receipt;
import com.sanitary.admin.entity.Shipment;
import com.sanitary.admin.entity.Statement;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.mapper.ReceiptMapper;
import com.sanitary.admin.mapper.ShipmentMapper;
import com.sanitary.admin.mapper.StatementMapper;
import com.sanitary.admin.service.StatementService;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatementServiceImpl extends ServiceImpl<StatementMapper, Statement> implements StatementService {

    private final GenerateNoUtil generateNoUtil;
    private final ReceiptMapper receiptMapper;
    private final ShipmentMapper shipmentMapper;
    private final CustomerMapper customerMapper;

    @Override
    public Page<Statement> pageList(int page, int size, Long customerId, String statementMonth) {
        LambdaQueryWrapper<Statement> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) {
            wrapper.eq(Statement::getCustomerId, customerId);
        }
        if (StringUtils.hasText(statementMonth)) {
            wrapper.eq(Statement::getStatementMonth, statementMonth);
        }
        wrapper.orderByDesc(Statement::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Statement generate(Long customerId, String statementMonth) {
        // Get customer info
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null) {
            throw new RuntimeException("客户不存在");
        }

        // Parse month range
        YearMonth ym = YearMonth.parse(statementMonth, DateTimeFormatter.ofPattern("yyyy-MM"));
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();

        // Calculate receipt totals for this customer in this month
        LambdaQueryWrapper<Receipt> receiptWrapper = new LambdaQueryWrapper<Receipt>()
                .eq(Receipt::getCustomerId, customerId)
                .eq(Receipt::getStatus, 1)
                .ge(Receipt::getReceiptDate, startDate)
                .le(Receipt::getReceiptDate, endDate);
        List<Receipt> receipts = receiptMapper.selectList(receiptWrapper);
        BigDecimal receiptQty = receipts.stream()
                .map(Receipt::getQuantity)
                .filter(q -> q != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal receiptAmount = receipts.stream()
                .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate shipment totals
        LambdaQueryWrapper<Shipment> shipmentWrapper = new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getCustomerId, customerId)
                .eq(Shipment::getStatus, 1)
                .ge(Shipment::getShipmentDate, startDate)
                .le(Shipment::getShipmentDate, endDate);
        List<Shipment> shipments = shipmentMapper.selectList(shipmentWrapper);
        BigDecimal shipmentQty = shipments.stream()
                .map(Shipment::getQuantity)
                .filter(q -> q != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shipmentAmount = shipments.stream()
                .map(s -> s.getAmount() != null ? s.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Check if statement already exists for this customer/month
        LambdaQueryWrapper<Statement> existWrapper = new LambdaQueryWrapper<Statement>()
                .eq(Statement::getCustomerId, customerId)
                .eq(Statement::getStatementMonth, statementMonth);
        Statement existing = getOne(existWrapper);
        if (existing != null) {
            // Update existing
            existing.setReceiptQty(receiptQty);
            existing.setShipmentQty(shipmentQty);
            existing.setReceiptAmount(receiptAmount);
            existing.setShipmentAmount(shipmentAmount);
            updateById(existing);
            return existing;
        }

        // Create new statement
        Statement statement = new Statement();
        statement.setStatementNo(generateNoUtil.generateMonthly("DZ", "statement", "statement_no"));
        statement.setStatementMonth(statementMonth);
        statement.setCustomerId(customerId);
        statement.setCustomerName(customer.getCustomerName());
        statement.setReceiptQty(receiptQty);
        statement.setShipmentQty(shipmentQty);
        statement.setReceiptAmount(receiptAmount);
        statement.setShipmentAmount(shipmentAmount);
        statement.setStatus("未确认");
        save(statement);
        return statement;
    }

    @Override
    @Transactional
    public void confirm(Long id) {
        Statement statement = getById(id);
        if (statement == null) {
            throw new RuntimeException("对账单不存在");
        }
        statement.setStatus("已确认");
        updateById(statement);
    }
}
