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
import com.sanitary.admin.util.ExcelExportUtil;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        wrapper.orderByDesc(Statement::getStatementMonth).orderByDesc(Statement::getId);
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

        // Query receipt items for this customer in this month
        LambdaQueryWrapper<Receipt> receiptWrapper = new LambdaQueryWrapper<Receipt>()
                .eq(Receipt::getCustomerId, customerId)
                .eq(Receipt::getStatus, 1)
                .ge(Receipt::getReceiptDate, startDate)
                .le(Receipt::getReceiptDate, endDate);
        List<Receipt> receipts = receiptMapper.selectList(receiptWrapper);
        List<Long> receiptIds = receipts.stream().map(Receipt::getId).collect(Collectors.toList());
        List<ReceiptItem> allReceiptItems = new ArrayList<>();
        BigDecimal receiptQty = BigDecimal.ZERO;
        BigDecimal receiptAmount = BigDecimal.ZERO;
        if (!receiptIds.isEmpty()) {
            allReceiptItems = receiptItemMapper.selectList(
                new LambdaQueryWrapper<ReceiptItem>().in(ReceiptItem::getReceiptId, receiptIds));
            receiptQty = allReceiptItems.stream()
                .map(ReceiptItem::getQuantity).filter(q -> q != null).reduce(BigDecimal.ZERO, BigDecimal::add);
            receiptAmount = allReceiptItems.stream()
                .filter(i -> !"返工".equals(i.getReceiptSource()))
                .map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Query shipment items for this customer in this month
        LambdaQueryWrapper<Shipment> shipmentWrapper = new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getCustomerId, customerId)
                .eq(Shipment::getStatus, 1)
                .ge(Shipment::getShipmentDate, startDate)
                .le(Shipment::getShipmentDate, endDate);
        List<Shipment> shipments = shipmentMapper.selectList(shipmentWrapper);
        List<Long> shipmentIds = shipments.stream().map(Shipment::getId).collect(Collectors.toList());
        List<ShipmentItem> allShipmentItems = new ArrayList<>();
        BigDecimal shipmentQty = BigDecimal.ZERO;
        BigDecimal shipmentAmount = BigDecimal.ZERO;
        if (!shipmentIds.isEmpty()) {
            allShipmentItems = shipmentItemMapper.selectList(
                new LambdaQueryWrapper<ShipmentItem>().in(ShipmentItem::getShipmentId, shipmentIds));
            shipmentQty = allShipmentItems.stream()
                .map(si -> safeAdd(si.getQuantity(), si.getDefectiveQty())).reduce(BigDecimal.ZERO, BigDecimal::add);
            shipmentAmount = allShipmentItems.stream()
                .map(i -> i.getAmount() != null ? i.getAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Check if statement already exists for this customer/month
        LambdaQueryWrapper<Statement> existWrapper = new LambdaQueryWrapper<Statement>()
                .eq(Statement::getCustomerId, customerId)
                .eq(Statement::getStatementMonth, statementMonth);
        Statement existing = getOne(existWrapper);
        if (existing != null) {
            // Update existing header
            existing.setReceiptQty(receiptQty);
            existing.setShipmentQty(shipmentQty);
            existing.setReceiptAmount(receiptAmount);
            existing.setShipmentAmount(shipmentAmount);
            updateById(existing);
            // Re-generate items
            statementItemService.deleteByStatementId(existing.getId());
            buildAndSaveItems(existing, customerId, ym, allReceiptItems, allShipmentItems);
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
        save(statement);
        buildAndSaveItems(statement, customerId, ym, allReceiptItems, allShipmentItems);
        return statement;
    }

    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        return (a != null ? a : BigDecimal.ZERO).add(b != null ? b : BigDecimal.ZERO);
    }

    private void buildAndSaveItems(Statement stmt, Long customerId, YearMonth ym,
                                   List<ReceiptItem> receiptItems, List<ShipmentItem> shipmentItems) {
        // Group by materialId + "_" + processId
        Map<String, StatementItem> itemMap = new LinkedHashMap<>();

        for (ReceiptItem ri : receiptItems) {
            Long mid = ri.getMaterialId() != null ? ri.getMaterialId() : 0L;
            Long pid = ri.getProcessId() != null ? ri.getProcessId() : 0L;
            String key = mid + "_" + pid;
            StatementItem item = itemMap.computeIfAbsent(key, k -> {
                StatementItem si = new StatementItem();
                si.setMaterialId(mid != 0L ? mid : null);
                si.setMaterialCode(ri.getMaterialCode());
                si.setMaterialName(ri.getMaterialName());
                si.setProcessId(pid != 0L ? pid : null);
                si.setProcessName(ri.getProcessName());
                si.setReceiptQty(BigDecimal.ZERO);
                si.setShipmentQty(BigDecimal.ZERO);
                si.setDefectiveQty(BigDecimal.ZERO);
                si.setGoodsAmount(BigDecimal.ZERO);
                si.setShipmentAmount(BigDecimal.ZERO);
                si.setPrevBalanceQty(BigDecimal.ZERO);
                si.setCurrBalanceQty(BigDecimal.ZERO);
                si.setUnitPrice(BigDecimal.ZERO);
                return si;
            });
            item.setReceiptQty(item.getReceiptQty().add(ri.getQuantity() != null ? ri.getQuantity() : BigDecimal.ZERO));
            // Use unit_price from receipt item if not set yet; skip rework rows (their price should be 0)
            if (!"返工".equals(ri.getReceiptSource()) && item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0
                    && ri.getUnitPrice() != null && ri.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                item.setUnitPrice(ri.getUnitPrice());
            }
        }

        for (ShipmentItem si : shipmentItems) {
            Long mid = si.getMaterialId() != null ? si.getMaterialId() : 0L;
            Long pid = si.getProcessId() != null ? si.getProcessId() : 0L;
            String key = mid + "_" + pid;
            StatementItem item = itemMap.computeIfAbsent(key, k -> {
                StatementItem newItem = new StatementItem();
                newItem.setMaterialId(mid != 0L ? mid : null);
                newItem.setMaterialCode(si.getMaterialCode());
                newItem.setMaterialName(si.getMaterialName());
                newItem.setProcessId(pid != 0L ? pid : null);
                newItem.setProcessName(si.getProcessName());
                newItem.setReceiptQty(BigDecimal.ZERO);
                newItem.setShipmentQty(BigDecimal.ZERO);
                newItem.setDefectiveQty(BigDecimal.ZERO);
                newItem.setGoodsAmount(BigDecimal.ZERO);
                newItem.setShipmentAmount(BigDecimal.ZERO);
                newItem.setPrevBalanceQty(BigDecimal.ZERO);
                newItem.setCurrBalanceQty(BigDecimal.ZERO);
                newItem.setUnitPrice(BigDecimal.ZERO);
                return newItem;
            });
            BigDecimal goodsQty = si.getQuantity() != null ? si.getQuantity() : BigDecimal.ZERO;
            BigDecimal defQty = si.getDefectiveQty() != null ? si.getDefectiveQty() : BigDecimal.ZERO;
            BigDecimal amt = si.getAmount() != null ? si.getAmount() : BigDecimal.ZERO;
            item.setShipmentQty(item.getShipmentQty().add(goodsQty).add(defQty));
            item.setDefectiveQty(item.getDefectiveQty().add(defQty));
            item.setGoodsAmount(item.getGoodsAmount().add(amt));
            item.setShipmentAmount(item.getShipmentAmount().add(amt));
            if (item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0 && si.getUnitPrice() != null
                    && si.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
                item.setUnitPrice(si.getUnitPrice());
            }
        }

        // Compute prevBalanceQty = sum(receipt_qty before monthStart) - sum(shipment_qty before monthStart)
        // for this customer, grouped by materialId+processId. This is independent of whether prior
        // statement records exist, making each month's generation idempotent.
        LocalDate monthStart = ym.atDay(1);

        // All receipts for this customer BEFORE the month start
        LambdaQueryWrapper<Receipt> prevReceiptWrapper = new LambdaQueryWrapper<Receipt>()
                .eq(Receipt::getCustomerId, customerId)
                .eq(Receipt::getStatus, 1)
                .lt(Receipt::getReceiptDate, monthStart);
        List<Receipt> prevReceipts = receiptMapper.selectList(prevReceiptWrapper);
        List<Long> prevReceiptIds = prevReceipts.stream().map(Receipt::getId).collect(Collectors.toList());

        // All shipments for this customer BEFORE the month start
        LambdaQueryWrapper<Shipment> prevShipmentWrapper = new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getCustomerId, customerId)
                .eq(Shipment::getStatus, 1)
                .lt(Shipment::getShipmentDate, monthStart);
        List<Shipment> prevShipments = shipmentMapper.selectList(prevShipmentWrapper);
        List<Long> prevShipmentIds = prevShipments.stream().map(Shipment::getId).collect(Collectors.toList());

        // key: materialId_processId -> net qty before this month
        Map<String, BigDecimal> prevBalanceMap = new HashMap<>();

        if (!prevReceiptIds.isEmpty()) {
            List<ReceiptItem> prevRItems = receiptItemMapper.selectList(
                new LambdaQueryWrapper<ReceiptItem>().in(ReceiptItem::getReceiptId, prevReceiptIds));
            for (ReceiptItem ri : prevRItems) {
                Long mid = ri.getMaterialId() != null ? ri.getMaterialId() : 0L;
                Long pid = ri.getProcessId() != null ? ri.getProcessId() : 0L;
                String key = mid + "_" + pid;
                BigDecimal qty = ri.getQuantity() != null ? ri.getQuantity() : BigDecimal.ZERO;
                prevBalanceMap.merge(key, qty, BigDecimal::add);
            }
        }

        if (!prevShipmentIds.isEmpty()) {
            List<ShipmentItem> prevSItems = shipmentItemMapper.selectList(
                new LambdaQueryWrapper<ShipmentItem>().in(ShipmentItem::getShipmentId, prevShipmentIds));
            for (ShipmentItem si : prevSItems) {
                Long mid = si.getMaterialId() != null ? si.getMaterialId() : 0L;
                Long pid = si.getProcessId() != null ? si.getProcessId() : 0L;
                String key = mid + "_" + pid;
                // shipmentQty = goodsQty + defectiveQty (same logic as current month)
                BigDecimal goodsQty = si.getQuantity() != null ? si.getQuantity() : BigDecimal.ZERO;
                BigDecimal defQty = si.getDefectiveQty() != null ? si.getDefectiveQty() : BigDecimal.ZERO;
                prevBalanceMap.merge(key, goodsQty.add(defQty).negate(), BigDecimal::add);
            }
        }

        List<StatementItem> itemsToSave = new ArrayList<>();
        for (Map.Entry<String, StatementItem> entry : itemMap.entrySet()) {
            StatementItem item = entry.getValue();
            // Fallback unit price from material
            if (item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0 && item.getMaterialId() != null) {
                Material mat = materialMapper.selectById(item.getMaterialId());
                if (mat != null && mat.getDefaultPrice() != null && mat.getDefaultPrice().compareTo(BigDecimal.ZERO) > 0) {
                    item.setUnitPrice(mat.getDefaultPrice());
                }
            }
            // prevBalanceQty
            BigDecimal prevBal = prevBalanceMap.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            item.setPrevBalanceQty(prevBal);
            // currBalanceQty = prevBal + receiptQty - shipmentQty
            item.setCurrBalanceQty(prevBal.add(item.getReceiptQty()).subtract(item.getShipmentQty()));
            itemsToSave.add(item);
        }

        if (!itemsToSave.isEmpty()) {
            statementItemService.saveItems(stmt.getId(), stmt.getStatementNo(), itemsToSave);
        }
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
                item.setPrevBalanceQty(parseQty(getCellString(row, 3)));
                item.setReceiptQty(parseQty(getCellString(row, 5)));
                item.setDefectiveQty(parseQty(getCellString(row, 7)));      // col7 = 原件退回数量
                item.setShipmentQty(parseQty(getCellString(row, 8)));
                item.setCurrBalanceQty(parseQty(getCellString(row, 9)));
                item.setUnitPrice(parsePrice(getCellString(row, 10)));
                item.setGoodsAmount(parsePrice(getCellString(row, 11)));    // col11 = 良品金额
                item.setShipmentAmount(parsePrice(getCellString(row, 12)));
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

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                : new java.math.BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield new java.math.BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString(); }
                catch (Exception e) { yield cell.getStringCellValue().trim(); }
            }
            default -> "";
        };
    }

    private java.math.BigDecimal parseQty(String s) {
        if (s == null || s.trim().isEmpty()) return java.math.BigDecimal.ZERO;
        try { return new java.math.BigDecimal(s.trim()).setScale(0, java.math.RoundingMode.HALF_UP); }
        catch (Exception e) { return java.math.BigDecimal.ZERO; }
    }

    private java.math.BigDecimal parsePrice(String s) {
        if (s == null || s.trim().isEmpty()) return java.math.BigDecimal.ZERO;
        try { return new java.math.BigDecimal(s.trim()).setScale(2, java.math.RoundingMode.HALF_UP); }
        catch (Exception e) { return java.math.BigDecimal.ZERO; }
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

    @Override
    public void exportExcel(HttpServletResponse response, Long customerId, String statementMonth) {
        LambdaQueryWrapper<Statement> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) wrapper.eq(Statement::getCustomerId, customerId);
        if (StringUtils.hasText(statementMonth)) wrapper.eq(Statement::getStatementMonth, statementMonth);
        wrapper.orderByDesc(Statement::getStatementMonth).orderByDesc(Statement::getId).last("LIMIT 5000");
        List<Statement> statements = this.list(wrapper);

        List<Long> ids = statements.stream().map(Statement::getId).collect(Collectors.toList());
        List<StatementItem> allItems = new ArrayList<>();
        if (!ids.isEmpty()) {
            for (Long stId : ids) {
                allItems.addAll(statementItemService.getByStatementId(stId));
            }
        }
        java.util.Map<Long, List<StatementItem>> itemMap = allItems.stream()
            .collect(Collectors.groupingBy(StatementItem::getStatementId));

        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("对账单");
        String[] headers = {"对账单号","对账月份","客户名称","物料编码","物料名称","工艺名称",
            "期初数量","本期收货","本期发货","废品数量","期末数量","单价","良品金额","发货金额","备注"};
        ExcelExportUtil.writeTitleRow(sheet, wb, "对账单", headers.length);
        ExcelExportUtil.writeHeaderRow(sheet, wb, headers);

        CellStyle masterS = ExcelExportUtil.masterRowStyle(wb);
        CellStyle s0 = ExcelExportUtil.dataStyle(wb, false);
        CellStyle s1 = ExcelExportUtil.dataStyle(wb, true);
        CellStyle n0 = ExcelExportUtil.numStyle(wb, false);
        CellStyle n1 = ExcelExportUtil.numStyle(wb, true);

        BigDecimal totalReceiptQty = BigDecimal.ZERO, totalShipmentQty = BigDecimal.ZERO,
            totalGoodsAmount = BigDecimal.ZERO, totalShipmentAmount = BigDecimal.ZERO;

        int rowIdx = 2;
        int detailCount = 0;
        for (Statement st : statements) {
            List<StatementItem> items = itemMap.getOrDefault(st.getId(), new ArrayList<>());
            Row masterRow = sheet.createRow(rowIdx++);
            ExcelExportUtil.setCell(masterRow, 0, st.getStatementNo(), masterS);
            ExcelExportUtil.setCell(masterRow, 1, st.getStatementMonth(), masterS);
            ExcelExportUtil.setCell(masterRow, 2, st.getCustomerName(), masterS);
            for (int i = 3; i < headers.length; i++) ExcelExportUtil.setCell(masterRow, i, "", masterS);

            for (StatementItem item : items) {
                if (detailCount >= 50000) break;
                boolean even = (detailCount % 2 == 0);
                CellStyle cs = even ? s0 : s1;
                CellStyle ns = even ? n0 : n1;
                Row row = sheet.createRow(rowIdx++);
                ExcelExportUtil.setCell(row, 0, "", cs);
                ExcelExportUtil.setCell(row, 1, "", cs);
                ExcelExportUtil.setCell(row, 2, "", cs);
                ExcelExportUtil.setCell(row, 3, item.getMaterialCode(), cs);
                ExcelExportUtil.setCell(row, 4, item.getMaterialName(), cs);
                ExcelExportUtil.setCell(row, 5, item.getProcessName(), cs);
                ExcelExportUtil.setCell(row, 6, item.getPrevBalanceQty(), ns);
                ExcelExportUtil.setCell(row, 7, item.getReceiptQty(), ns);
                ExcelExportUtil.setCell(row, 8, item.getShipmentQty(), ns);
                ExcelExportUtil.setCell(row, 9, item.getDefectiveQty(), ns);
                ExcelExportUtil.setCell(row, 10, item.getCurrBalanceQty(), ns);
                ExcelExportUtil.setCell(row, 11, item.getUnitPrice(), ns);
                ExcelExportUtil.setCell(row, 12, item.getGoodsAmount(), ns);
                ExcelExportUtil.setCell(row, 13, item.getShipmentAmount(), ns);
                ExcelExportUtil.setCell(row, 14, item.getRemark(), cs);
                if (item.getReceiptQty() != null) totalReceiptQty = totalReceiptQty.add(item.getReceiptQty());
                if (item.getShipmentQty() != null) totalShipmentQty = totalShipmentQty.add(item.getShipmentQty());
                if (item.getGoodsAmount() != null) totalGoodsAmount = totalGoodsAmount.add(item.getGoodsAmount());
                if (item.getShipmentAmount() != null) totalShipmentAmount = totalShipmentAmount.add(item.getShipmentAmount());
                detailCount++;
            }
        }

        CellStyle sumS = ExcelExportUtil.summaryStyle(wb);
        CellStyle sumN = ExcelExportUtil.summaryNumStyle(wb);
        Row sumRow = sheet.createRow(rowIdx);
        ExcelExportUtil.setCell(sumRow, 0, "合计", sumS);
        for (int i = 1; i <= 6; i++) ExcelExportUtil.setCell(sumRow, i, "", sumS);
        ExcelExportUtil.setCell(sumRow, 7, totalReceiptQty, sumN);
        ExcelExportUtil.setCell(sumRow, 8, totalShipmentQty, sumN);
        ExcelExportUtil.setCell(sumRow, 9, "", sumS);
        ExcelExportUtil.setCell(sumRow, 10, "", sumS);
        ExcelExportUtil.setCell(sumRow, 11, "", sumS);
        ExcelExportUtil.setCell(sumRow, 12, totalGoodsAmount, sumN);
        ExcelExportUtil.setCell(sumRow, 13, totalShipmentAmount, sumN);
        ExcelExportUtil.setCell(sumRow, 14, "", sumS);

        sheet.createFreezePane(0, 2);
        ExcelExportUtil.autoSize(sheet, headers.length);
        try {
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            ExcelExportUtil.writeResponse(wb, response, "对账单_" + today + ".xlsx");
        } catch (IOException e) {
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public java.util.Map<String, Object> generateAll() {
        int success = 0, skip = 0, fail = 0;
        java.util.List<String> errors = new java.util.ArrayList<>();

        // 从收货单聚合出所有 (customerId, yyyy-MM) 组合
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        List<Receipt> allReceipts = receiptMapper.selectList(
            new LambdaQueryWrapper<Receipt>().eq(Receipt::getStatus, 1).select(Receipt::getCustomerId, Receipt::getReceiptDate));
        for (Receipt r : allReceipts) {
            if (r.getReceiptDate() != null) {
                keys.add(r.getCustomerId() + "_" + r.getReceiptDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
            }
        }
        // 再从发货单补充
        List<Shipment> allShipments = shipmentMapper.selectList(
            new LambdaQueryWrapper<Shipment>().eq(Shipment::getStatus, 1).select(Shipment::getCustomerId, Shipment::getShipmentDate));
        for (Shipment s : allShipments) {
            if (s.getShipmentDate() != null) {
                keys.add(s.getCustomerId() + "_" + s.getShipmentDate().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
            }
        }

        for (String key : keys) {
            String[] parts = key.split("_");
            Long customerId = Long.valueOf(parts[0]);
            String month = parts[1];
            // 跳过已存在的
            if (count(new LambdaQueryWrapper<Statement>()
                    .eq(Statement::getCustomerId, customerId)
                    .eq(Statement::getStatementMonth, month)) > 0) {
                skip++;
                continue;
            }
            try {
                generate(customerId, month);
                success++;
            } catch (Exception e) {
                fail++;
                errors.add(key + ": " + e.getMessage());
            }
        }

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", success);
        result.put("skip", skip);
        result.put("fail", fail);
        result.put("errors", errors);
        return result;
    }
}
