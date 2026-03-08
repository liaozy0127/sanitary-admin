package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.entity.Inventory;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Process;
import com.sanitary.admin.entity.Receipt;
import com.sanitary.admin.entity.ReceiptItem;
import com.sanitary.admin.entity.Shipment;
import com.sanitary.admin.entity.ShipmentItem;
import com.sanitary.admin.entity.Statement;
import com.sanitary.admin.entity.StatementItem;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.mapper.InventoryMapper;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ProcessMapper;
import com.sanitary.admin.mapper.ReceiptItemMapper;
import com.sanitary.admin.mapper.ReceiptMapper;
import com.sanitary.admin.mapper.ShipmentItemMapper;
import com.sanitary.admin.mapper.ShipmentMapper;
import com.sanitary.admin.mapper.StatementItemMapper;
import com.sanitary.admin.mapper.StatementMapper;
import com.sanitary.admin.service.StatementItemService;
import com.sanitary.admin.service.StatementService;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatementServiceImpl extends ServiceImpl<StatementMapper, Statement> implements StatementService {

    private final GenerateNoUtil generateNoUtil;
    private final ReceiptMapper receiptMapper;
    private final ReceiptItemMapper receiptItemMapper;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentItemMapper shipmentItemMapper;
    private final CustomerMapper customerMapper;
    private final StatementItemService statementItemService;
    private final MaterialMapper materialMapper;
    private final ProcessMapper processMapper;
    private final InventoryMapper inventoryMapper;

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
        // Aggregate qty and amount from receipt_item
        List<Long> receiptIds = receipts.stream().map(Receipt::getId).collect(java.util.stream.Collectors.toList());
        BigDecimal receiptQty = BigDecimal.ZERO;
        BigDecimal receiptAmount = BigDecimal.ZERO;
        if (!receiptIds.isEmpty()) {
            List<ReceiptItem> receiptItems = receiptItemMapper.selectList(
                new LambdaQueryWrapper<ReceiptItem>().in(ReceiptItem::getReceiptId, receiptIds));
            receiptQty = receiptItems.stream()
                .map(ReceiptItem::getQuantity).filter(q -> q != null).reduce(BigDecimal.ZERO, BigDecimal::add);
            receiptAmount = receiptItems.stream()
                .map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Calculate shipment totals
        LambdaQueryWrapper<Shipment> shipmentWrapper = new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getCustomerId, customerId)
                .eq(Shipment::getStatus, 1)
                .ge(Shipment::getShipmentDate, startDate)
                .le(Shipment::getShipmentDate, endDate);
        List<Shipment> shipments = shipmentMapper.selectList(shipmentWrapper);
        // Aggregate qty and amount from shipment_item
        List<Long> shipmentIds = shipments.stream().map(Shipment::getId).collect(java.util.stream.Collectors.toList());
        BigDecimal shipmentQty = BigDecimal.ZERO;
        BigDecimal shipmentAmount = BigDecimal.ZERO;
        if (!shipmentIds.isEmpty()) {
            List<ShipmentItem> shipmentItems = shipmentItemMapper.selectList(
                new LambdaQueryWrapper<ShipmentItem>().in(ShipmentItem::getShipmentId, shipmentIds));
            shipmentQty = shipmentItems.stream()
                .map(ShipmentItem::getQuantity).filter(q -> q != null).reduce(BigDecimal.ZERO, BigDecimal::add);
            shipmentAmount = shipmentItems.stream()
                .map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

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
    public java.util.Map<String, Object> importExcel(
            org.springframework.web.multipart.MultipartFile file,
            Long customerId, String statementMonth, Boolean initInventory) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        java.util.List<String> errors = new java.util.ArrayList<>();

        // 幂等检查
        LambdaQueryWrapper<Statement> existCheck = new LambdaQueryWrapper<Statement>()
            .eq(Statement::getCustomerId, customerId)
            .eq(Statement::getStatementMonth, statementMonth);
        if (count(existCheck) > 0) {
            result.put("success", 0); result.put("skip", 1); result.put("fail", 0);
            result.put("inventoryInit", false); result.put("msg", "该客户该月份对账单已存在");
            return result;
        }

        // 读取 Excel
        java.util.List<com.sanitary.admin.entity.StatementItem> items = new java.util.ArrayList<>();
        try (java.io.InputStream is = file.getInputStream();
             org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(is)) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                if (row == null) continue;
                String col0 = getCellString(row, 0);
                if (col0.isEmpty() || col0.contains("合计") || col0.contains("应收金额")) continue;

                com.sanitary.admin.entity.StatementItem item = new com.sanitary.admin.entity.StatementItem();
                item.setMaterialCode(col0);
                item.setMaterialName(getCellString(row, 1));
                item.setProcessName(getCellString(row, 2));
                item.setPrevBalanceQty(parseBigDecimal(getCellString(row, 3)));
                item.setReceiptQty(parseBigDecimal(getCellString(row, 5)));
                item.setShipmentQty(parseBigDecimal(getCellString(row, 8)));
                item.setCurrBalanceQty(parseBigDecimal(getCellString(row, 9)));
                item.setUnitPrice(parseBigDecimal(getCellString(row, 10)));
                item.setShipmentAmount(parseBigDecimal(getCellString(row, 12)));
                item.setRemark(getCellString(row, 13));

                // 查 material_id
                if (org.springframework.util.StringUtils.hasText(item.getMaterialCode())) {
                    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sanitary.admin.entity.Material> mw =
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sanitary.admin.entity.Material>()
                        .eq(com.sanitary.admin.entity.Material::getMaterialCode, item.getMaterialCode())
                        .eq(com.sanitary.admin.entity.Material::getCustomerId, customerId)
                        .last("LIMIT 1");
                    com.sanitary.admin.entity.Material mat = materialMapper.selectOne(mw);
                    if (mat != null) { item.setMaterialId(mat.getId()); item.setMaterialName(mat.getMaterialName()); }
                }
                // 查 process_id
                if (org.springframework.util.StringUtils.hasText(item.getProcessName())) {
                    com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sanitary.admin.entity.Process> pw =
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.sanitary.admin.entity.Process>()
                        .eq(com.sanitary.admin.entity.Process::getProcessName, item.getProcessName())
                        .last("LIMIT 1");
                    com.sanitary.admin.entity.Process proc = processMapper.selectOne(pw);
                    if (proc != null) item.setProcessId(proc.getId());
                }
                items.add(item);
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel解析失败: " + e.getMessage());
        }

        // 汇总
        java.math.BigDecimal totalReceiptQty = items.stream().map(com.sanitary.admin.entity.StatementItem::getReceiptQty)
            .filter(v -> v != null).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal totalShipmentQty = items.stream().map(com.sanitary.admin.entity.StatementItem::getShipmentQty)
            .filter(v -> v != null).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        java.math.BigDecimal totalShipmentAmount = items.stream().map(com.sanitary.admin.entity.StatementItem::getShipmentAmount)
            .filter(v -> v != null).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        com.sanitary.admin.entity.Customer cust = customerMapper.selectById(customerId);
        String customerName = cust != null ? cust.getCustomerName() : "";

        Statement statement = new Statement();
        statement.setStatementNo(generateNoUtil.generate("DZ", "statement", "statement_no"));
        statement.setStatementMonth(statementMonth);
        statement.setCustomerId(customerId);
        statement.setCustomerName(customerName);
        statement.setReceiptQty(totalReceiptQty);
        statement.setShipmentQty(totalShipmentQty);
        statement.setShipmentAmount(totalShipmentAmount);
        statement.setStatus("草稿");
        getBaseMapper().insert(statement);
        statementItemService.saveItems(statement.getId(), statement.getStatementNo(), items);

        // 库存初始化
        boolean inventoryInit = false;
        String inventorySkipped = null;
        int inventoryCount = 0;
        if (Boolean.TRUE.equals(initInventory)) {
            long invCount = inventoryMapper.selectCount(null);
            if (invCount > 0) {
                inventorySkipped = "库存已有 " + invCount + " 条数据，跳过初始化";
            } else {
                for (com.sanitary.admin.entity.StatementItem item : items) {
                    if (item.getPrevBalanceQty() == null || item.getPrevBalanceQty().compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
                    try {
                        com.sanitary.admin.entity.Inventory inv = new com.sanitary.admin.entity.Inventory();
                        inv.setMaterialId(item.getMaterialId());
                        inv.setCustomerId(customerId);
                        inv.setProcessId(item.getProcessId() != null ? item.getProcessId() : 0L);
                        inv.setMaterialCode(item.getMaterialCode());
                        inv.setMaterialName(item.getMaterialName());
                        inv.setCustomerName(customerName);
                        inv.setProcessName(item.getProcessName() != null ? item.getProcessName() : "");
                        inv.setQuantity(item.getPrevBalanceQty());
                        inventoryMapper.insert(inv);
                        inventoryCount++;
                    } catch (Exception e) {
                        errors.add("库存初始化失败[" + item.getMaterialCode() + "]: " + e.getMessage());
                    }
                }
                inventoryInit = true;
            }
        }

        result.put("success", 1);
        result.put("skip", 0);
        result.put("fail", 0);
        result.put("itemCount", items.size());
        result.put("inventoryInit", inventoryInit);
        result.put("inventoryCount", inventoryCount);
        if (inventorySkipped != null) result.put("inventorySkipped", inventorySkipped);
        result.put("errors", errors);
        return result;
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

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf((long) cell.getNumericCellValue()); }
                catch (Exception e) { yield cell.getStringCellValue().trim(); }
            }
            default -> "";
        };
    }

    private java.math.BigDecimal parseBigDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return java.math.BigDecimal.ZERO;
        try { return new java.math.BigDecimal(s.trim()); }
        catch (Exception e) { return java.math.BigDecimal.ZERO; }
    }

    public Statement getByIdWithItems(Long id) {
        Statement s = getById(id);
        if (s != null) {
            s.setItems(statementItemService.getByStatementId(id));
        }
        return s;
    }
}
