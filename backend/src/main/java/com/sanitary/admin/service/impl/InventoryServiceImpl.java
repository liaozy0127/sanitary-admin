package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Inventory;
import com.sanitary.admin.entity.InventoryLog;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Process;
import com.sanitary.admin.mapper.InventoryLogMapper;
import com.sanitary.admin.mapper.InventoryMapper;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ProcessMapper;
import com.sanitary.admin.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory> implements InventoryService {

    private final InventoryLogMapper inventoryLogMapper;
    private final MaterialMapper materialMapper;
    private final ProcessMapper processMapper;

    @Override
    @Transactional
    public void updateInventory(Long materialId, Long customerId, Long processId, String materialCode, String materialName,
                                String customerName, String spec, String processName,
                                BigDecimal changeQty, int changeType, String orderType,
                                Long orderId, String orderNo, LocalDate orderDate) {

        // 将 null 的 processId 统一处理为 0L，确保唯一约束正常工作
        Long effectiveProcessId = processId != null ? processId : 0L;

        // 如果是发货（changeType == 2），需要先检查库存是否足够
        if (changeType == 2) { // 发货
            LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Inventory::getMaterialId, materialId)
                    .eq(Inventory::getCustomerId, customerId)
                    .eq(Inventory::getProcessId, effectiveProcessId);

            Inventory inventory = this.getOne(queryWrapper, false);
            if (inventory != null) {
                BigDecimal currentQty = inventory.getQuantity() != null ? inventory.getQuantity() : BigDecimal.ZERO;
                BigDecimal shipQty = changeQty.abs(); // changeQty 是负数，取绝对值
                if (currentQty.compareTo(shipQty) < 0) {
                    throw new RuntimeException("库存不足，当前库存：" + currentQty + "，发货数量：" + shipQty);
                }
            } else {
                // 如果库存记录不存在，而又要发货，则不允许
                throw new RuntimeException("库存不足，当前库存：0，发货数量：" + changeQty.abs());
            }
        }

        // 首先尝试原子更新现有库存记录
        int rowsAffected = this.baseMapper.incrementQuantity(materialId, customerId, effectiveProcessId, changeQty);

        if (rowsAffected == 0) {
            // 如果没有更新任何记录，则说明库存记录不存在，需要插入新记录
            Inventory inventory = new Inventory();
            inventory.setMaterialId(materialId);
            inventory.setCustomerId(customerId);
            inventory.setProcessId(effectiveProcessId);
            inventory.setMaterialCode(materialCode);
            inventory.setMaterialName(materialName);
            inventory.setCustomerName(customerName);
            inventory.setSpec(spec);
            inventory.setProcessName(processName);
            inventory.setQuantity(changeQty);

            // 设置时间
            if (changeType == 1) { // 收货
                inventory.setLastReceiveTime(LocalDateTime.now());
            } else if (changeType == 2) { // 发货
                inventory.setLastShipTime(LocalDateTime.now());
            }

            this.save(inventory);

            // 记录库存变动日志
            insertLog(materialId, customerId, effectiveProcessId, materialCode, materialName, customerName, spec, processName,
                    changeType, changeQty, BigDecimal.ZERO, changeQty, orderType, orderId, orderNo, orderDate, null);
        } else {
            // 查询更新后的库存数量用于记录日志
            LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Inventory::getMaterialId, materialId)
                    .eq(Inventory::getCustomerId, customerId)
                    .eq(Inventory::getProcessId, effectiveProcessId);

            Inventory inventory = this.getOne(queryWrapper, false);
            BigDecimal afterQty = inventory != null ? inventory.getQuantity() : changeQty;
            BigDecimal beforeQty = afterQty.subtract(changeQty);

            // 记录库存变动日志
            insertLog(materialId, customerId, effectiveProcessId, materialCode, materialName, customerName, spec, processName,
                    changeType, changeQty, beforeQty, afterQty, orderType, orderId, orderNo, orderDate, null);
        }
    }

    /**
     * 插入库存变动日志
     */
    private void insertLog(Long materialId, Long customerId, Long processId, String materialCode, String materialName,
                           String customerName, String spec, String processName, int changeType, BigDecimal changeQty,
                           BigDecimal beforeQty, BigDecimal afterQty, String orderType, Long orderId, String orderNo,
                           LocalDate orderDate, String remark) {
        insertLog(materialId, customerId, processId, materialCode, materialName, customerName, spec, processName,
                changeType, changeQty, beforeQty, afterQty, orderType, orderId, orderNo, orderDate, remark, null);
    }

    /**
     * 插入库存变动日志（支持指定 createTime，用于历史数据重建）
     */
    private void insertLog(Long materialId, Long customerId, Long processId, String materialCode, String materialName,
                           String customerName, String spec, String processName, int changeType, BigDecimal changeQty,
                           BigDecimal beforeQty, BigDecimal afterQty, String orderType, Long orderId, String orderNo,
                           LocalDate orderDate, String remark, LocalDateTime createTime) {
        InventoryLog log = new InventoryLog();
        log.setMaterialId(materialId);
        log.setCustomerId(customerId);
        log.setProcessId(processId);
        log.setMaterialCode(materialCode);
        log.setMaterialName(materialName);
        log.setCustomerName(customerName);
        log.setSpec(spec);
        log.setProcessName(processName);
        log.setChangeType(changeType);
        log.setChangeQty(changeQty);
        log.setBeforeQty(beforeQty);
        log.setAfterQty(afterQty);
        log.setOrderType(orderType);
        log.setOrderId(orderId);
        log.setOrderNo(orderNo);
        log.setOrderDate(orderDate);
        log.setRemark(remark);
        if (createTime != null) {
            log.setCreateTime(createTime);
        }

        inventoryLogMapper.insert(log);
    }

    @Override
    public IPage<Inventory> pageList(int page, int size, Long customerId, String keyword) {
        LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.gt(Inventory::getQuantity, BigDecimal.ZERO); // 只显示有库存的

        if (customerId != null) {
            queryWrapper.eq(Inventory::getCustomerId, customerId);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper.like(Inventory::getMaterialCode, keyword)
                    .or()
                    .like(Inventory::getMaterialName, keyword)
                    .or()
                    .like(Inventory::getCustomerName, keyword));
        }

        queryWrapper.orderByDesc(Inventory::getCreateTime);

        return this.page(new Page<>(page, size), queryWrapper);
    }

    @Override
    public IPage<InventoryLog> logPageList(int page, int size, Long materialId, Long customerId, Integer changeType,
                                           String startDate, String endDate) {
        LambdaQueryWrapper<InventoryLog> queryWrapper = new LambdaQueryWrapper<>();

        if (materialId != null) {
            queryWrapper.eq(InventoryLog::getMaterialId, materialId);
        }

        if (customerId != null) {
            queryWrapper.eq(InventoryLog::getCustomerId, customerId);
        }

        if (changeType != null) {
            queryWrapper.eq(InventoryLog::getChangeType, changeType);
        }

        if (startDate != null && !startDate.isEmpty()) {
            queryWrapper.ge(InventoryLog::getCreateTime, startDate + " 00:00:00");
        }

        if (endDate != null && !endDate.isEmpty()) {
            queryWrapper.le(InventoryLog::getCreateTime, endDate + " 23:59:59");
        }

        queryWrapper.orderByDesc(InventoryLog::getCreateTime);

        return inventoryLogMapper.selectPage(new Page<>(page, size), queryWrapper);
    }

    @Override
    @Transactional
    public Map<String, Object> initFromStatement(MultipartFile file) {
        int success = 0;
        int fail = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();

        String today = LocalDate.now().toString().replace("-", "");

        try (java.io.InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);

            // Skip first 2 rows (headers)
            for (int i = 2; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String materialCode = getCellString(row, 0); // 产品代码 (col 0)
                    String materialName = getCellString(row, 1); // 产品名称 (col 1)
                    String processName = getCellString(row, 2); // 工艺要求 (col 2)
                    String quantityStr = getCellString(row, 3); // 结余数量 (col 3)

                    if (materialCode.isEmpty()) {
                        fail++;
                        errors.add("第" + (i + 1) + "行: 产品代码不能为空");
                        continue;
                    }

                    // Find material by material code to get customer_id
                    Material material = materialMapper.selectOne(
                        new LambdaQueryWrapper<Material>()
                            .eq(Material::getMaterialCode, materialCode.trim())
                    );

                    if (material == null) {
                        fail++;
                        errors.add("第" + (i + 1) + "行: 物料代码「" + materialCode + "」不存在");
                        continue;
                    }

                    Long processId = null;
                    if (!processName.isEmpty()) {
                        Process process = processMapper.selectOne(
                            new LambdaQueryWrapper<Process>()
                                .eq(Process::getProcessName, processName.trim())
                        );
                        if (process != null) {
                            processId = process.getId();
                        } else {
                            fail++;
                            errors.add("第" + (i + 1) + "行: 工艺「" + processName + "」不存在");
                            continue;
                        }
                    }

                    BigDecimal quantity = parseBigDecimal(quantityStr);
                    if (quantity.compareTo(BigDecimal.ZERO) < 0) {
                        fail++;
                        errors.add("第" + (i + 1) + "行: 结余数量不能为负数");
                        continue;
                    }

                    // Find existing inventory record
                    LambdaQueryWrapper<Inventory> invQuery = new LambdaQueryWrapper<>();
                    invQuery.eq(Inventory::getMaterialId, material.getId())
                            .eq(Inventory::getCustomerId, material.getCustomerId())
                            .eq(Inventory::getProcessId, processId != null ? processId : 0L);

                    Inventory existingInventory = this.getOne(invQuery, false);

                    if (existingInventory != null) {
                        // Update existing inventory
                        BigDecimal oldQty = existingInventory.getQuantity() != null ? existingInventory.getQuantity() : BigDecimal.ZERO;

                        // Calculate the difference to update the quantity
                        BigDecimal diff = quantity.subtract(oldQty);

                        if (diff.compareTo(BigDecimal.ZERO) != 0) {
                            existingInventory.setQuantity(quantity);
                            this.updateById(existingInventory);

                            // Log the change (type 4 = 初始化)
                            insertLog(material.getId(), material.getCustomerId(), processId,
                                     material.getMaterialCode(), material.getMaterialName(),
                                     material.getCustomerName(), material.getSpec(), processName,
                                     4, diff, oldQty, quantity, "INVENTORY_INIT", null, "INIT-" + today,
                                     LocalDate.now(), "库存初始化");
                        }

                        skip++; // Count as skip since we're updating existing records
                    } else {
                        // Create new inventory record
                        Inventory inventory = new Inventory();
                        inventory.setMaterialId(material.getId());
                        inventory.setCustomerId(material.getCustomerId());
                        inventory.setProcessId(processId != null ? processId : 0L);
                        inventory.setMaterialCode(material.getMaterialCode());
                        inventory.setMaterialName(material.getMaterialName());
                        inventory.setCustomerName(material.getCustomerName());
                        inventory.setSpec(material.getSpec());
                        inventory.setProcessName(processName);
                        inventory.setQuantity(quantity);
                        inventory.setCreateTime(LocalDateTime.now());
                        inventory.setUpdateTime(LocalDateTime.now());

                        this.save(inventory);

                        // Log the initial quantity (type 4 = 初始化)
                        insertLog(material.getId(), material.getCustomerId(), processId,
                                 material.getMaterialCode(), material.getMaterialName(),
                                 material.getCustomerName(), material.getSpec(), processName,
                                 4, quantity, BigDecimal.ZERO, quantity, "INVENTORY_INIT", null, "INIT-" + today,
                                 LocalDate.now(), "库存初始化");

                        success++;
                    }

                } catch (Exception e) {
                    fail++;
                    errors.add("第" + (i + 1) + "行: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel解析失败: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("fail", fail);
        result.put("skip", skip);
        result.put("errors", errors);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> rebuildFromOrders() {
        // 1. 清空现有库存和日志
        this.baseMapper.delete(null);
        inventoryLogMapper.delete(null);

        // key: "materialId_customerId_processId" -> 运行中库存数量（用于流水 beforeQty/afterQty）
        java.util.HashMap<String, BigDecimal> runningQty = new java.util.HashMap<>();

        // 2. 按时间顺序处理收货明细，生成收货流水
        List<Map<String, Object>> receiptItems = this.baseMapper.listAllReceiptItems();
        int receiptLogs = 0;
        for (Map<String, Object> row : receiptItems) {
            Long materialId = toLong(row.get("material_id"));
            Long customerId = toLong(row.get("customer_id"));
            Long processId = toLong(row.get("process_id"));
            String key = materialId + "_" + customerId + "_" + processId;

            BigDecimal changeQty = toBigDecimal(row.get("quantity"));
            BigDecimal beforeQty = runningQty.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal afterQty = beforeQty.add(changeQty);
            runningQty.put(key, afterQty);

            LocalDate orderDate = toLocalDate(row.get("order_date"));
            LocalDateTime createTime = orderDate != null ? orderDate.atStartOfDay() : LocalDateTime.now();
            insertLog(materialId, customerId, processId,
                    str(row.get("material_code")), str(row.get("material_name")),
                    str(row.get("customer_name")), str(row.get("spec")), str(row.get("process_name")),
                    1, changeQty, beforeQty, afterQty,
                    "receipt", toLong(row.get("order_id")), str(row.get("order_no")), orderDate, null, createTime);
            receiptLogs++;
        }

        // 3. 按时间顺序处理发货明细，生成发货流水
        List<Map<String, Object>> shipmentItems = this.baseMapper.listAllShipmentItems();
        int shipmentLogs = 0;
        for (Map<String, Object> row : shipmentItems) {
            Long materialId = toLong(row.get("material_id"));
            Long customerId = toLong(row.get("customer_id"));
            Long processId = toLong(row.get("process_id"));
            String key = materialId + "_" + customerId + "_" + processId;

            BigDecimal totalQty = toBigDecimal(row.get("total_qty"));
            if (totalQty.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal beforeQty = runningQty.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal afterQty = beforeQty.subtract(totalQty);
            runningQty.put(key, afterQty);

            LocalDate orderDate = toLocalDate(row.get("order_date"));
            LocalDateTime createTime = orderDate != null ? orderDate.atStartOfDay() : LocalDateTime.now();
            insertLog(materialId, customerId, processId,
                    str(row.get("material_code")), str(row.get("material_name")),
                    str(row.get("customer_name")), str(row.get("spec")), str(row.get("process_name")),
                    2, totalQty.negate(), beforeQty, afterQty,
                    "shipment", toLong(row.get("order_id")), str(row.get("order_no")), orderDate, null, createTime);
            shipmentLogs++;
        }

        // 4. 将 runningQty 中的最终值保存为库存快照
        // 同时补充物料/客户名称等维度信息（从收货明细聚合获取）
        List<Map<String, Object>> receiptAggs = this.baseMapper.aggregateReceiptQty();
        java.util.HashMap<String, Map<String, Object>> dimMap = new java.util.HashMap<>();
        for (Map<String, Object> row : receiptAggs) {
            Long materialId = toLong(row.get("material_id"));
            Long customerId = toLong(row.get("customer_id"));
            Long processId = toLong(row.get("process_id"));
            dimMap.put(materialId + "_" + customerId + "_" + processId, row);
        }

        // 加载发货聚合，用于取最后发货时间
        List<Map<String, Object>> shipmentAggs = this.baseMapper.aggregateShipmentQty();
        java.util.HashMap<String, Map<String, Object>> shipDimMap = new java.util.HashMap<>();
        for (Map<String, Object> row : shipmentAggs) {
            Long materialId = toLong(row.get("material_id"));
            Long customerId = toLong(row.get("customer_id"));
            Long processId = toLong(row.get("process_id"));
            shipDimMap.put(materialId + "_" + customerId + "_" + processId, row);
        }

        int saved = 0;
        for (Map.Entry<String, BigDecimal> entry : runningQty.entrySet()) {
            String key = entry.getKey();
            BigDecimal qty = entry.getValue();
            String[] parts = key.split("_");
            Long materialId = Long.parseLong(parts[0]);
            Long customerId = Long.parseLong(parts[1]);
            Long processId = Long.parseLong(parts[2]);

            Inventory inv = new Inventory();
            inv.setMaterialId(materialId);
            inv.setCustomerId(customerId);
            inv.setProcessId(processId);
            inv.setQuantity(qty);
            inv.setCreateTime(LocalDateTime.now());
            inv.setUpdateTime(LocalDateTime.now());

            Map<String, Object> dim = dimMap.get(key);
            if (dim != null) {
                inv.setMaterialCode(str(dim.get("material_code")));
                inv.setMaterialName(str(dim.get("material_name")));
                inv.setCustomerName(str(dim.get("customer_name")));
                inv.setSpec(str(dim.get("spec")));
                inv.setProcessName(str(dim.get("process_name")));
                // 使用实际最后收货日期而非今日
                LocalDate lastRecvDate = toLocalDate(dim.get("last_receive_date"));
                if (lastRecvDate != null) {
                    inv.setLastReceiveTime(lastRecvDate.atStartOfDay());
                }
            }

            // 设置最后发货时间
            Map<String, Object> shipDim = shipDimMap.get(key);
            if (shipDim != null) {
                LocalDate lastShipDate = toLocalDate(shipDim.get("last_ship_date"));
                if (lastShipDate != null) {
                    inv.setLastShipTime(lastShipDate.atStartOfDay());
                }
            }

            this.save(inv);
            saved++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("inventoryRecords", saved);
        result.put("receiptLogs", receiptLogs);
        result.put("shipmentLogs", shipmentLogs);
        return result;
    }

    private LocalDate toLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate) return (LocalDate) v;
        if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate();
        try { return LocalDate.parse(v.toString().substring(0, 10)); }
        catch (Exception e) { return null; }
    }

    private Long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Long) return (Long) v;
        if (v instanceof Number) return ((Number) v).longValue();
        return Long.parseLong(v.toString());
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return new BigDecimal(v.toString());
        try { return new BigDecimal(v.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String str(Object v) {
        return v == null ? null : v.toString();
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

    private BigDecimal parseBigDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }
}