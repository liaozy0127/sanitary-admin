package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
        // 使用关联查询获取最新的客户名称
        IPage<Map<String, Object>> mapPage = this.baseMapper.pageListWithJoin(new Page<>(page, size), customerId, statementMonth);

        // 转换为 Statement 对象（兼容前端）
        Page<Statement> result = new Page<>(page, size, mapPage.getTotal());
        List<Statement> records = new ArrayList<>();
        for (Map<String, Object> map : mapPage.getRecords()) {
            Statement stmt = new Statement();
            stmt.setId(toLong(map.get("id")));
            stmt.setStatementNo(str(map.get("statement_no")));
            stmt.setStatementMonth(str(map.get("statement_month")));
            stmt.setCustomerId(toLong(map.get("customer_id")));
            stmt.setCustomerName(str(map.get("customer_name")));
            stmt.setReceiptQty(toBigDecimal(map.get("receipt_qty")));
            stmt.setShipmentQty(toBigDecimal(map.get("shipment_qty")));
            stmt.setReceiptAmount(toBigDecimal(map.get("receipt_amount")));
            stmt.setGoodsAmount(toBigDecimal(map.get("goods_amount")));
            stmt.setShipmentAmount(toBigDecimal(map.get("shipment_amount")));
            stmt.setRemark(str(map.get("remark")));
            records.add(stmt);
        }
        result.setRecords(records);
        return result;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long) return (Long) v;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); }
        catch (Exception e) { return null; }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return new BigDecimal(v.toString());
        try { return new BigDecimal(v.toString()); }
        catch (Exception e) { return null; }
    }

    private String str(Object v) {
        return v == null ? null : v.toString();
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
            BigDecimal existingGoodsAmount = buildAndSaveItems(existing, customerId, ym, receipts, allReceiptItems, shipments, allShipmentItems);
            existing.setGoodsAmount(existingGoodsAmount);
            // 更新 shipmentAmount 为明细汇总（含结转行）
            existing.setShipmentAmount(existingGoodsAmount);
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
        save(statement);
        BigDecimal newGoodsAmount = buildAndSaveItems(statement, customerId, ym, receipts, allReceiptItems, shipments, allShipmentItems);
        statement.setGoodsAmount(newGoodsAmount);
        // 更新 shipmentAmount 为明细汇总（含结转行）
        statement.setShipmentAmount(newGoodsAmount);
        updateById(statement);
        return statement;
    }

    private BigDecimal safeAdd(BigDecimal a, BigDecimal b) {
        return (a != null ? a : BigDecimal.ZERO).add(b != null ? b : BigDecimal.ZERO);
    }

    private BigDecimal buildAndSaveItems(Statement stmt, Long customerId, YearMonth ym,
                                   List<Receipt> receipts, List<ReceiptItem> receiptItems,
                                   List<Shipment> shipments, List<ShipmentItem> shipmentItems) {
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
                si.setNormalReceiptQty(BigDecimal.ZERO);
                si.setReworkQty(BigDecimal.ZERO);
                si.setShipmentQty(BigDecimal.ZERO);
                si.setGoodsShipQty(BigDecimal.ZERO);
                si.setDefectiveQty(BigDecimal.ZERO);
                si.setGoodsAmount(BigDecimal.ZERO);
                si.setReworkAmount(BigDecimal.ZERO);
                si.setPrevFinancialBalance(BigDecimal.ZERO);
                si.setShipmentAmount(BigDecimal.ZERO);
                si.setPrevBalanceQty(BigDecimal.ZERO);
                si.setCurrBalanceQty(BigDecimal.ZERO);
                si.setUnitPrice(BigDecimal.ZERO);
                return si;
            });
            BigDecimal riQty = ri.getQuantity() != null ? ri.getQuantity() : BigDecimal.ZERO;
            item.setReceiptQty(item.getReceiptQty().add(riQty));
            // Accumulate rework qty separately (these are free)
            if ("返工".equals(ri.getReceiptSource())) {
                item.setReworkQty(item.getReworkQty().add(riQty));
            } else {
                item.setNormalReceiptQty(item.getNormalReceiptQty().add(riQty));
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
                newItem.setNormalReceiptQty(BigDecimal.ZERO);
                newItem.setReworkQty(BigDecimal.ZERO);
                newItem.setShipmentQty(BigDecimal.ZERO);
                newItem.setGoodsShipQty(BigDecimal.ZERO);
                newItem.setDefectiveQty(BigDecimal.ZERO);
                newItem.setGoodsAmount(BigDecimal.ZERO);
                newItem.setReworkAmount(BigDecimal.ZERO);
                newItem.setPrevFinancialBalance(BigDecimal.ZERO);
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
            item.setShipmentAmount(item.getShipmentAmount().add(amt));
        }

        // ---- 取价：发货单最新一条优先，其次收货单最新一条（非返工）----
        // 构建 shipmentId -> shipmentDate 映射
        Map<Long, java.time.LocalDate> shipmentDateMap = new HashMap<>();
        for (Shipment s : shipments) {
            if (s.getShipmentDate() != null) shipmentDateMap.put(s.getId(), s.getShipmentDate());
        }
        // 构建 receiptId -> receiptDate 映射
        Map<Long, java.time.LocalDate> receiptDateMap = new HashMap<>();
        for (Receipt rc : receipts) {
            if (rc.getReceiptDate() != null) receiptDateMap.put(rc.getId(), rc.getReceiptDate());
        }

        // 每个 key 的最新发货单价
        Map<String, BigDecimal> latestShipPriceMap = new HashMap<>();
        Map<String, java.time.LocalDate> latestShipDateMap = new HashMap<>();
        for (ShipmentItem si : shipmentItems) {
            if (si.getUnitPrice() == null || si.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) continue;
            Long mid = si.getMaterialId() != null ? si.getMaterialId() : 0L;
            Long pid = si.getProcessId() != null ? si.getProcessId() : 0L;
            String key = mid + "_" + pid;
            java.time.LocalDate date = shipmentDateMap.get(si.getShipmentId());
            java.time.LocalDate existing = latestShipDateMap.get(key);
            if (existing == null || (date != null && date.isAfter(existing))) {
                latestShipPriceMap.put(key, si.getUnitPrice());
                latestShipDateMap.put(key, date != null ? date : java.time.LocalDate.MIN);
            }
        }

        // 每个 key 的最新收货单价（非返工）
        Map<String, BigDecimal> latestRcptPriceMap = new HashMap<>();
        Map<String, java.time.LocalDate> latestRcptDateMap = new HashMap<>();
        for (ReceiptItem ri : receiptItems) {
            if ("返工".equals(ri.getReceiptSource())) continue;
            if (ri.getUnitPrice() == null || ri.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) continue;
            Long mid = ri.getMaterialId() != null ? ri.getMaterialId() : 0L;
            Long pid = ri.getProcessId() != null ? ri.getProcessId() : 0L;
            String key = mid + "_" + pid;
            java.time.LocalDate date = receiptDateMap.get(ri.getReceiptId());
            java.time.LocalDate existing = latestRcptDateMap.get(key);
            if (existing == null || (date != null && date.isAfter(existing))) {
                latestRcptPriceMap.put(key, ri.getUnitPrice());
                latestRcptDateMap.put(key, date != null ? date : java.time.LocalDate.MIN);
            }
        }

        // Compute prevBalanceQty = sum(receipt_qty before monthStart) - sum(shipment_qty before monthStart)
        // for this customer, grouped by materialId+processId. This is independent of whether prior
        // statement records exist, making each month's generation idempotent.
        LocalDate monthStart = ym.atDay(1);

        // ---- 查上月对账明细，用于处理上期结转 ----
        // 业务规则：仅当上月对账单【总金额】为负时才结转
        // 对每条上月负金额明细：若本月有相同 materialId+processId → 合并行（加返工数量和金额）
        //                       若本月没有 → 新增幽灵行（收发货为0，只有返工数量/金额）
        YearMonth prevYm = ym.minusMonths(1);
        String prevMonth = prevYm.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        LambdaQueryWrapper<Statement> prevStmtWrapper = new LambdaQueryWrapper<Statement>()
            .eq(Statement::getCustomerId, customerId)
            .eq(Statement::getStatementMonth, prevMonth)
            .last("LIMIT 1");
        Statement prevStatement = getOne(prevStmtWrapper);
        // 上月需结转的明细列表（只在上月总金额为负时使用）
        List<StatementItem> prevCarryItems = new ArrayList<>();
        if (prevStatement != null) {
            List<StatementItem> prevItems = statementItemService.getByStatementId(prevStatement.getId());
            // 计算上月总金额
            BigDecimal prevTotal = prevItems.stream()
                .map(pi -> pi.getShipmentAmount() != null ? pi.getShipmentAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 仅当上月总金额为负时才结转
            if (prevTotal.compareTo(BigDecimal.ZERO) < 0) {
                for (StatementItem pi : prevItems) {
                    BigDecimal itemAmt = pi.getShipmentAmount() != null ? pi.getShipmentAmount() : BigDecimal.ZERO;
                    if (itemAmt.compareTo(BigDecimal.ZERO) < 0) {
                        prevCarryItems.add(pi);
                    }
                }
            }
        }

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

        // ---- 反推法：基于当前库存快照计算结余 ----
        // 1. 读取当前库存快照
        LambdaQueryWrapper<Inventory> invWrapper = new LambdaQueryWrapper<Inventory>()
            .eq(Inventory::getCustomerId, customerId);
        List<Inventory> inventories = inventoryMapper.selectList(invWrapper);
        Map<String, BigDecimal> inventoryMap = new HashMap<>();
        for (Inventory inv : inventories) {
            Long mid = inv.getMaterialId() != null ? inv.getMaterialId() : 0L;
            Long pid = inv.getProcessId() != null ? inv.getProcessId() : 0L;
            inventoryMap.put(mid + "_" + pid, inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO);
        }

        // 2. 对账月结束后的收货
        LocalDate monthEnd = ym.atEndOfMonth();
        LambdaQueryWrapper<Receipt> afterReceiptWrapper = new LambdaQueryWrapper<Receipt>()
            .eq(Receipt::getCustomerId, customerId)
            .eq(Receipt::getStatus, 1)
            .gt(Receipt::getReceiptDate, monthEnd);
        List<Receipt> afterReceipts = receiptMapper.selectList(afterReceiptWrapper);
        List<Long> afterReceiptIds = afterReceipts.stream().map(Receipt::getId).collect(Collectors.toList());
        Map<String, BigDecimal> afterReceiptMap = new HashMap<>();
        if (!afterReceiptIds.isEmpty()) {
            List<ReceiptItem> afterRItems = receiptItemMapper.selectList(
                new LambdaQueryWrapper<ReceiptItem>().in(ReceiptItem::getReceiptId, afterReceiptIds));
            for (ReceiptItem ri : afterRItems) {
                Long mid = ri.getMaterialId() != null ? ri.getMaterialId() : 0L;
                Long pid = ri.getProcessId() != null ? ri.getProcessId() : 0L;
                String k = mid + "_" + pid;
                BigDecimal qty = ri.getQuantity() != null ? ri.getQuantity() : BigDecimal.ZERO;
                afterReceiptMap.merge(k, qty, BigDecimal::add);
            }
        }

        // 3. 对账月结束后的发货
        LambdaQueryWrapper<Shipment> afterShipmentWrapper = new LambdaQueryWrapper<Shipment>()
            .eq(Shipment::getCustomerId, customerId)
            .eq(Shipment::getStatus, 1)
            .gt(Shipment::getShipmentDate, monthEnd);
        List<Shipment> afterShipments = shipmentMapper.selectList(afterShipmentWrapper);
        List<Long> afterShipmentIds = afterShipments.stream().map(Shipment::getId).collect(Collectors.toList());
        Map<String, BigDecimal> afterShipmentMap = new HashMap<>();
        if (!afterShipmentIds.isEmpty()) {
            List<ShipmentItem> afterSItems = shipmentItemMapper.selectList(
                new LambdaQueryWrapper<ShipmentItem>().in(ShipmentItem::getShipmentId, afterShipmentIds));
            for (ShipmentItem si : afterSItems) {
                Long mid = si.getMaterialId() != null ? si.getMaterialId() : 0L;
                Long pid = si.getProcessId() != null ? si.getProcessId() : 0L;
                String k = mid + "_" + pid;
                BigDecimal goodsQty = si.getQuantity() != null ? si.getQuantity() : BigDecimal.ZERO;
                BigDecimal defQty = si.getDefectiveQty() != null ? si.getDefectiveQty() : BigDecimal.ZERO;
                afterShipmentMap.merge(k, goodsQty.add(defQty), BigDecimal::add);
            }
        }

        List<StatementItem> itemsToSave = new ArrayList<>();
        for (Map.Entry<String, StatementItem> entry : itemMap.entrySet()) {
            StatementItem item = entry.getValue();
            String key = entry.getKey();
            // 取价：发货单最新 > 收货单最新 > 物料默认价
            BigDecimal unitPrice = latestShipPriceMap.get(key);
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0) {
                unitPrice = latestRcptPriceMap.get(key);
            }
            if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
                item.setUnitPrice(unitPrice);
            }
            // Fallback unit price from material
            if (item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0 && item.getMaterialId() != null) {
                Material mat = materialMapper.selectById(item.getMaterialId());
                if (mat != null && mat.getDefaultPrice() != null && mat.getDefaultPrice().compareTo(BigDecimal.ZERO) > 0) {
                    item.setUnitPrice(mat.getDefaultPrice());
                }
            }
            // 计算结余：优先使用反推法（当前库存 - 月后净变动），无库存记录时退化为单据累加
            BigDecimal prevBal;
            BigDecimal currBal;
            if (inventoryMap.containsKey(key)) {
                BigDecimal invQty = inventoryMap.get(key);
                BigDecimal afterReceipt = afterReceiptMap.getOrDefault(key, BigDecimal.ZERO);
                BigDecimal afterShipment = afterShipmentMap.getOrDefault(key, BigDecimal.ZERO);
                currBal = invQty.subtract(afterReceipt).add(afterShipment);
                prevBal = currBal.subtract(item.getReceiptQty()).add(item.getShipmentQty());
            } else {
                prevBal = prevBalanceMap.getOrDefault(key, BigDecimal.ZERO);
                currBal = prevBal.add(item.getReceiptQty()).subtract(item.getShipmentQty());
            }
            item.setPrevBalanceQty(prevBal);
            item.setCurrBalanceQty(currBal);
            // 发货金额（良品）= 发货总数量 × 单价
            BigDecimal shipQty = item.getShipmentQty() != null ? item.getShipmentQty() : BigDecimal.ZERO;
            BigDecimal goodsAmount = shipQty.multiply(item.getUnitPrice()).setScale(2, java.math.RoundingMode.HALF_UP);
            // 发货金额（返工）= -本月收货（返工）× 单价（负数，表示抵扣）
            BigDecimal reworkQty = item.getReworkQty() != null ? item.getReworkQty() : BigDecimal.ZERO;
            BigDecimal reworkAmount = reworkQty.multiply(item.getUnitPrice()).negate().setScale(2, java.math.RoundingMode.HALF_UP);
            // 发货金额（合计）= 良品金额 + 返工金额
            BigDecimal totalAmount = goodsAmount.add(reworkAmount);
            item.setGoodsShipQty(shipQty);
            item.setGoodsAmount(goodsAmount);
            item.setReworkAmount(reworkAmount);
            item.setPrevFinancialBalance(BigDecimal.ZERO);
            item.setPrevFinancialOrigin(null);
            item.setShipmentAmount(totalAmount);
            itemsToSave.add(item);
        }

        // ---- 处理上期结转：上月总金额为负时，将上月负金额明细合并或新增到本月 ----
        for (StatementItem pi : prevCarryItems) {
            Long mid = pi.getMaterialId() != null ? pi.getMaterialId() : 0L;
            Long pid = pi.getProcessId() != null ? pi.getProcessId() : 0L;
            String k = mid + "_" + pid;
            // 上月该明细的返工数量和返工金额（已经是负数）
            BigDecimal carryReworkQty = pi.getReworkQty() != null ? pi.getReworkQty() : BigDecimal.ZERO;
            BigDecimal carryReworkAmt = pi.getReworkAmount() != null ? pi.getReworkAmount() : BigDecimal.ZERO;

            // 查本月 itemMap 是否已有该 key
            StatementItem existing = itemMap.get(k);
            if (existing != null) {
                // 合并：把上月的返工数量和返工金额加入本月已有行
                BigDecimal existReworkQty = existing.getReworkQty() != null ? existing.getReworkQty() : BigDecimal.ZERO;
                BigDecimal existReworkAmt = existing.getReworkAmount() != null ? existing.getReworkAmount() : BigDecimal.ZERO;
                existing.setReworkQty(existReworkQty.add(carryReworkQty));
                existing.setReworkAmount(existReworkAmt.add(carryReworkAmt));
                // 重算该行 shipmentAmount
                BigDecimal mergedTotal = (existing.getGoodsAmount() != null ? existing.getGoodsAmount() : BigDecimal.ZERO)
                    .add(existing.getReworkAmount());
                existing.setShipmentAmount(mergedTotal);
            } else {
                // 幽灵行：收发货为0，只有返工数量/金额（来自上月结转）
                StatementItem ghost = new StatementItem();
                ghost.setMaterialId(mid != 0L ? mid : null);
                ghost.setMaterialCode(pi.getMaterialCode());
                ghost.setMaterialName(pi.getMaterialName());
                ghost.setProcessId(pid != 0L ? pid : null);
                ghost.setProcessName(pi.getProcessName());
                ghost.setPrevBalanceQty(BigDecimal.ZERO);
                ghost.setReceiptQty(BigDecimal.ZERO);
                ghost.setNormalReceiptQty(BigDecimal.ZERO);
                ghost.setReworkQty(carryReworkQty);
                ghost.setShipmentQty(BigDecimal.ZERO);
                ghost.setGoodsShipQty(BigDecimal.ZERO);
                ghost.setDefectiveQty(BigDecimal.ZERO);
                ghost.setCurrBalanceQty(BigDecimal.ZERO);
                ghost.setUnitPrice(pi.getUnitPrice() != null ? pi.getUnitPrice() : BigDecimal.ZERO);
                ghost.setGoodsAmount(BigDecimal.ZERO);
                ghost.setReworkAmount(carryReworkAmt);
                ghost.setPrevFinancialBalance(BigDecimal.ZERO);
                ghost.setPrevFinancialOrigin(null);
                ghost.setShipmentAmount(carryReworkAmt);
                itemsToSave.add(ghost);
                itemMap.put(k, ghost);
            }
        }

        if (!itemsToSave.isEmpty()) {
            statementItemService.saveItems(stmt.getId(), stmt.getStatementNo(), itemsToSave);
        }
        // Return total shipmentAmount (良品金额+返工抵扣后的实收合计) for this statement
        return itemsToSave.stream()
            .map(i -> i.getShipmentAmount() != null ? i.getShipmentAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
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
        java.math.BigDecimal totalGoodsAmount = items.stream().map(com.sanitary.admin.entity.StatementItem::getGoodsAmount)
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
        statement.setGoodsAmount(totalGoodsAmount);
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
        String[] headers = {"对账单号","对账月份","客户名称","产品编码","产品名称","规格型号","工艺要求",
            "上月结余",
            "正常","返工","合计",
            "良品","合计",
            "本月结余",
            "单价",
            "良品","返工","合计",
            "备注"};
        ExcelExportUtil.writeTitleRow(sheet, wb, "对账单", headers.length);
        // 二级分组表头：行1=分组，行2=子列
        // 独立列：0-7（对账单号~上月结余）、13（本月结余）、14（单价）、18（备注）
        // 本月收货：8-10，本月发货：11-12，发货金额：15-17
        ExcelExportUtil.writeTwoLevelHeaderRows(sheet, wb,
            new int[]{0,1,2,3,4,5,6,7,13,14,18},
            new int[][]{{8,9,10},{11,12},{15,16,17}},
            new String[]{"本月收货","本月发货","发货金额"},
            headers);

        CellStyle masterS = ExcelExportUtil.masterRowStyle(wb);
        CellStyle s0 = ExcelExportUtil.dataStyle(wb, false);
        CellStyle s1 = ExcelExportUtil.dataStyle(wb, true);
        CellStyle n0 = ExcelExportUtil.numStyle(wb, false);
        CellStyle n1 = ExcelExportUtil.numStyle(wb, true);
        CellStyle q0 = ExcelExportUtil.qtyStyle(wb, false);
        CellStyle q1 = ExcelExportUtil.qtyStyle(wb, true);

        BigDecimal totalReceiptQty = BigDecimal.ZERO, totalNormalReceiptQty = BigDecimal.ZERO,
            totalReworkReceiptQty = BigDecimal.ZERO, totalGoodsShipQty = BigDecimal.ZERO,
            totalShipmentQty = BigDecimal.ZERO, totalGoodsAmount = BigDecimal.ZERO,
            totalReworkAmount = BigDecimal.ZERO,
            totalTotalAmount = BigDecimal.ZERO,
            totalReceivable = BigDecimal.ZERO;

        int rowIdx = 3;
        int detailCount = 0;
        for (Statement st : statements) {
            List<StatementItem> items = itemMap.getOrDefault(st.getId(), new ArrayList<>());
            Row masterRow = sheet.createRow(rowIdx++);
            ExcelExportUtil.setCell(masterRow, 0, st.getStatementNo(), masterS);
            ExcelExportUtil.setCell(masterRow, 1, st.getStatementMonth(), masterS);
            ExcelExportUtil.setCell(masterRow, 2, st.getCustomerName(), masterS);
            for (int i = 3; i < headers.length; i++) ExcelExportUtil.setCell(masterRow, i, "", masterS);
            if (st.getShipmentAmount() != null) totalReceivable = totalReceivable.add(st.getShipmentAmount());

            for (StatementItem item : items) {
                if (detailCount >= 50000) break;
                boolean even = (detailCount % 2 == 0);
                CellStyle cs = even ? s0 : s1;
                CellStyle ns = even ? n0 : n1;
                CellStyle qs = even ? q0 : q1;
                Row row = sheet.createRow(rowIdx++);
                ExcelExportUtil.setCell(row, 0, "", cs);
                ExcelExportUtil.setCell(row, 1, "", cs);
                ExcelExportUtil.setCell(row, 2, "", cs);
                ExcelExportUtil.setCell(row, 3, item.getMaterialCode(), cs);
                ExcelExportUtil.setCell(row, 4, item.getMaterialName(), cs);
                ExcelExportUtil.setCell(row, 5, item.getSpec(), cs);
                ExcelExportUtil.setCell(row, 6, item.getProcessName(), cs);
                ExcelExportUtil.setCell(row, 7, item.getPrevBalanceQty(), qs);
                BigDecimal normalReceiptQty = item.getNormalReceiptQty() != null ? item.getNormalReceiptQty() : BigDecimal.ZERO;
                BigDecimal reworkReceiptQty = item.getReworkQty() != null ? item.getReworkQty() : BigDecimal.ZERO;
                BigDecimal receiptTotal = item.getReceiptQty() != null ? item.getReceiptQty() : BigDecimal.ZERO;
                BigDecimal shipTotalQty = item.getShipmentQty() != null ? item.getShipmentQty() : BigDecimal.ZERO;
                BigDecimal goodsAmtVal = item.getGoodsAmount() != null ? item.getGoodsAmount() : BigDecimal.ZERO;
                BigDecimal reworkAmtVal = item.getReworkAmount() != null ? item.getReworkAmount() : BigDecimal.ZERO;
                BigDecimal totalAmtVal = item.getShipmentAmount() != null ? item.getShipmentAmount() : BigDecimal.ZERO;
                ExcelExportUtil.setCell(row, 8, normalReceiptQty, qs);
                ExcelExportUtil.setCell(row, 9, reworkReceiptQty, qs);
                ExcelExportUtil.setCell(row, 10, receiptTotal, qs);
                ExcelExportUtil.setCell(row, 11, shipTotalQty, qs);  // 本月发货(良品) = 发货总数
                ExcelExportUtil.setCell(row, 12, shipTotalQty, qs);  // 本月发货(合计) = 同上
                ExcelExportUtil.setCell(row, 13, item.getCurrBalanceQty(), qs);
                ExcelExportUtil.setCell(row, 14, item.getUnitPrice(), ns);
                ExcelExportUtil.setCell(row, 15, goodsAmtVal, ns);
                ExcelExportUtil.setCell(row, 16, reworkAmtVal, ns);
                ExcelExportUtil.setCell(row, 17, totalAmtVal, ns);       // 发货金额(合计)
                ExcelExportUtil.setCell(row, 18, item.getRemark(), cs);
                totalReceiptQty = totalReceiptQty.add(receiptTotal);
                totalNormalReceiptQty = totalNormalReceiptQty.add(normalReceiptQty);
                totalReworkReceiptQty = totalReworkReceiptQty.add(reworkReceiptQty);
                totalGoodsShipQty = totalGoodsShipQty.add(shipTotalQty);
                totalShipmentQty = totalShipmentQty.add(shipTotalQty);
                totalGoodsAmount = totalGoodsAmount.add(goodsAmtVal);
                totalReworkAmount = totalReworkAmount.add(reworkAmtVal);
                totalTotalAmount = totalTotalAmount.add(totalAmtVal);
                detailCount++;
            }
        }

        CellStyle sumS = ExcelExportUtil.summaryStyle(wb);
        CellStyle sumN = ExcelExportUtil.summaryNumStyle(wb);
        CellStyle sumQ = ExcelExportUtil.summaryQtyStyle(wb);
        Row sumRow = sheet.createRow(rowIdx);
        ExcelExportUtil.setCell(sumRow, 0, "合计", sumS);
        for (int i = 1; i <= 7; i++) ExcelExportUtil.setCell(sumRow, i, "", sumS);
        ExcelExportUtil.setCell(sumRow, 8, totalNormalReceiptQty, sumQ);
        ExcelExportUtil.setCell(sumRow, 9, totalReworkReceiptQty, sumQ);
        ExcelExportUtil.setCell(sumRow, 10, totalReceiptQty, sumQ);
        ExcelExportUtil.setCell(sumRow, 11, totalGoodsShipQty, sumQ);
        ExcelExportUtil.setCell(sumRow, 12, totalShipmentQty, sumQ);
        ExcelExportUtil.setCell(sumRow, 13, "", sumS);
        ExcelExportUtil.setCell(sumRow, 14, "", sumS);
        ExcelExportUtil.setCell(sumRow, 15, totalGoodsAmount, sumN);
        ExcelExportUtil.setCell(sumRow, 16, totalReworkAmount, sumN);
        ExcelExportUtil.setCell(sumRow, 17, totalTotalAmount, sumN);
        ExcelExportUtil.setCell(sumRow, 18, "", sumS);

        // 应收金额行（Statement 级别 shipmentAmount 之和，含结转扣减）
        Row receivableRow = sheet.createRow(rowIdx + 1);
        CellStyle receivableLabel = ExcelExportUtil.summaryStyle(wb);
        for (int i = 0; i < headers.length; i++) ExcelExportUtil.setCell(receivableRow, i, "", receivableLabel);
        ExcelExportUtil.setCell(receivableRow, 0, "应收金额", receivableLabel);
        ExcelExportUtil.setCell(receivableRow, 17, totalReceivable, sumN);

        sheet.createFreezePane(0, 3);
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

        // ⚠️ 必须按月份从早到晚排序，确保 prevFinancialBalance 结转正确
        // key 格式: customerId_yyyy-MM，按月份字段（下划线后的部分）升序排序
        List<String> sortedKeys = new java.util.ArrayList<>(keys);
        sortedKeys.sort((a, b) -> {
            String monthA = a.substring(a.indexOf('_') + 1);
            String monthB = b.substring(b.indexOf('_') + 1);
            return monthA.compareTo(monthB);
        });

        for (String key : sortedKeys) {
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
