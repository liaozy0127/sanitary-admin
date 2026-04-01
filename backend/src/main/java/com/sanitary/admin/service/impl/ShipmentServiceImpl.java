package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Process;
import com.sanitary.admin.entity.Shipment;
import com.sanitary.admin.entity.ShipmentItem;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.mapper.InventoryMapper;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ProcessMapper;
import com.sanitary.admin.mapper.ShipmentMapper;
import com.sanitary.admin.service.MaterialProcessPriceService;
import com.sanitary.admin.service.InventoryService;
import com.sanitary.admin.service.ShipmentItemService;
import com.sanitary.admin.service.ShipmentService;
import com.sanitary.admin.util.ExcelExportUtil;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl extends ServiceImpl<ShipmentMapper, Shipment> implements ShipmentService {

    private final GenerateNoUtil generateNoUtil;
    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;
    private final ShipmentItemService shipmentItemService;
    private final CustomerMapper customerMapper;
    private final MaterialMapper materialMapper;
    private final ProcessMapper processMapper;
    private final MaterialProcessPriceService materialProcessPriceService;

    @Override
    public Page<Shipment> pageList(int page, int size, String keyword, Long customerId,
                                   String startDate, String endDate) {
        LambdaQueryWrapper<Shipment> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Shipment::getShipmentNo, keyword)
                    .or().like(Shipment::getCustomerName, keyword));
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
        wrapper.orderByDesc(Shipment::getShipmentDate).orderByDesc(Shipment::getId);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Shipment createShipment(Shipment shipment) {
        shipment.setShipmentNo(generateNoUtil.generate("FH", "shipment", "shipment_no"));
        if (shipment.getStatus() == null) {
            shipment.setStatus(1);
        }
        save(shipment);

        if (shipment.getItems() != null && !shipment.getItems().isEmpty()) {
            shipmentItemService.saveItems(shipment.getId(), shipment.getShipmentNo(), shipment.getItems());
            syncMaterialPrices(shipment.getItems(), shipment.getCustomerId(), shipment.getCustomerName());

            // 更新库存 - 发货出库（良品+废品均扣减库存）
            for (ShipmentItem item : shipment.getItems()) {
                BigDecimal totalQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                BigDecimal defQty = item.getDefectiveQty() != null ? item.getDefectiveQty() : BigDecimal.ZERO;
                BigDecimal shipTotal = totalQty.add(defQty);
                if (shipTotal.compareTo(BigDecimal.ZERO) <= 0) continue;
                inventoryService.updateInventory(
                    item.getMaterialId(),
                    shipment.getCustomerId(),
                    item.getProcessId(),
                    item.getMaterialCode(),
                    item.getMaterialName(),
                    shipment.getCustomerName(),
                    item.getSpec(),
                    item.getProcessName(),
                    shipTotal.negate(),  // 发货出库，数量取负
                    2,  // changeType: 2=发货(出库)
                    "shipment",  // orderType
                    shipment.getId(),
                    shipment.getShipmentNo(),
                    shipment.getShipmentDate()
                );
                // 注意：发货不操作返工库存，返工库存仅在收货来源为"返工"时设置
            }
        }

        return shipment;
    }

    @Override
    @Transactional
    public Shipment updateShipment(Shipment shipment) {
        // 若请求未传 shipmentNo，从数据库补充（避免明细插入时 NOT NULL 约束报错）
        if (shipment.getShipmentNo() == null) {
            Shipment existing = getById(shipment.getId());
            if (existing != null) {
                shipment.setShipmentNo(existing.getShipmentNo());
            }
        }

        // 先查询旧的明细，用于冲销库存
        List<ShipmentItem> oldItems = shipmentItemService.listByShipmentId(shipment.getId());

        // 先删除旧的明细
        shipmentItemService.deleteByShipmentId(shipment.getId());

        // 更新主表
        updateById(shipment);

        // 保存新的明细
        if (shipment.getItems() != null && !shipment.getItems().isEmpty()) {
            shipmentItemService.saveItems(shipment.getId(), shipment.getShipmentNo(), shipment.getItems());
            syncMaterialPrices(shipment.getItems(), shipment.getCustomerId(), shipment.getCustomerName());
        }
        
        // 冲销旧库存（归还库存，用 changeType=1 绕过库存不足检查）
        for (ShipmentItem oldItem : oldItems) {
            BigDecimal totalQty = oldItem.getQuantity() != null ? oldItem.getQuantity() : BigDecimal.ZERO;
            BigDecimal defQty = oldItem.getDefectiveQty() != null ? oldItem.getDefectiveQty() : BigDecimal.ZERO;
            BigDecimal shipTotal = totalQty.add(defQty);
            if (shipTotal.compareTo(BigDecimal.ZERO) <= 0) continue;
            inventoryService.updateInventory(
                oldItem.getMaterialId(),
                shipment.getCustomerId(),
                oldItem.getProcessId(),
                oldItem.getMaterialCode(),
                oldItem.getMaterialName(),
                shipment.getCustomerName(),
                oldItem.getSpec(),
                oldItem.getProcessName(),
                shipTotal,  // 正数归还库存
                1,  // changeType: 1=收货(入库)，绕过发货库存不足检查
                "shipment",
                shipment.getId(),
                shipment.getShipmentNo(),
                shipment.getShipmentDate()
            );
            // 注意：发货不操作返工库存，冲销时也不需要操作
        }

        // 更新新库存
        if (shipment.getItems() != null && !shipment.getItems().isEmpty()) {
            for (ShipmentItem item : shipment.getItems()) {
                BigDecimal totalQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
                BigDecimal defQty = item.getDefectiveQty() != null ? item.getDefectiveQty() : BigDecimal.ZERO;
                BigDecimal shipTotal = totalQty.add(defQty);
                if (shipTotal.compareTo(BigDecimal.ZERO) <= 0) continue;
                inventoryService.updateInventory(
                    item.getMaterialId(),
                    shipment.getCustomerId(),
                    item.getProcessId(),
                    item.getMaterialCode(),
                    item.getMaterialName(),
                    shipment.getCustomerName(),
                    item.getSpec(),
                    item.getProcessName(),
                    shipTotal.negate(),  // 发货出库，数量取负
                    2,  // changeType: 2=发货(出库)
                    "shipment",  // orderType
                    shipment.getId(),
                    shipment.getShipmentNo(),
                    shipment.getShipmentDate()
                );
                // 注意：发货不操作返工库存
            }
        }
        
        return shipment;
    }

    @Override
    @Transactional
    public boolean deleteShipment(Long id) {
        // 查询明细，用于冲销库存
        List<ShipmentItem> items = shipmentItemService.listByShipmentId(id);
        // 获取发货单信息
        Shipment shipment = getById(id);
        
        // 先删除明细
        shipmentItemService.deleteByShipmentId(id);
        
        // 冲销库存（归还库存，用 changeType=1 绕过库存不足检查）
        for (ShipmentItem item : items) {
            BigDecimal totalQty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ZERO;
            BigDecimal defQty = item.getDefectiveQty() != null ? item.getDefectiveQty() : BigDecimal.ZERO;
            BigDecimal shipTotal = totalQty.add(defQty);
            if (shipTotal.compareTo(BigDecimal.ZERO) <= 0) continue;
            inventoryService.updateInventory(
                item.getMaterialId(),
                shipment.getCustomerId(),
                item.getProcessId(),
                item.getMaterialCode(),
                item.getMaterialName(),
                shipment.getCustomerName(),
                item.getSpec(),
                item.getProcessName(),
                shipTotal,  // 正数归还库存
                1,  // changeType: 1=收货(入库)，绕过发货库存不足检查
                "shipment",
                shipment.getId(),
                shipment.getShipmentNo(),
                shipment.getShipmentDate()
            );
            // 注意：发货不操作返工库存，删除时也不需要操作
        }
        
        // 再删除主表记录
        return removeById(id);
    }

    @Override
    public Map<String, Object> importExcel(MultipartFile file, String mode) {
        int success = 0;
        int fail = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Shipment> shipmentMap = new LinkedHashMap<>();
        Map<String, List<ShipmentItem>> itemsMap = new LinkedHashMap<>();

        String lastShipmentNo = "";

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    // 列0=发货单号, 1=发货子单号, 2=日期, 3=客户名称, 4=产品名称,
                    // 5=型号规格, 6=工艺名称, 7=良品数量, 8=废品数量, 9=收货来源,
                    // 10=良品单价, 11=良品金额, 12=合计数量, 13=制单人, 16=明细备注, 18=客户单号
                    String shipmentNo = getCellString(row, 0);
                    if (shipmentNo.isEmpty()) {
                        shipmentNo = lastShipmentNo;
                    } else {
                        lastShipmentNo = shipmentNo;
                    }

                    if (shipmentNo.isEmpty()) {
                        skip++;
                        continue;
                    }

                    // 跳过 FG 前缀（返工单混入发货单文件中，数量均为0）
                    if (shipmentNo.startsWith("FG")) {
                        skip++;
                        continue;
                    }

                    if (shipmentExists(shipmentNo)) {
                        skip++;
                        continue;
                    }

                    if (!shipmentMap.containsKey(shipmentNo)) {
                        Shipment shipment = new Shipment();
                        shipment.setShipmentNo(shipmentNo);
                        shipment.setShipmentDate(parseExcelDate(getCellString(row, 2)));
                        shipment.setCustomerName(getCellString(row, 3));
                        shipment.setOperator(getCellString(row, 13)); // 列13=制单人
                        shipment.setStatus(1);

                        Long customerId = findOrCreateCustomerIdByName(shipment.getCustomerName());
                        shipment.setCustomerId(customerId);

                        shipmentMap.put(shipmentNo, shipment);
                        itemsMap.put(shipmentNo, new ArrayList<>());
                    }

                    ShipmentItem item = new ShipmentItem();
                    item.setShipmentNo(shipmentNo);
                    item.setMaterialName(getCellString(row, 4));
                    item.setSpec(getCellString(row, 5));
                    item.setProcessName(getCellString(row, 6));
                    item.setQuantity(parseQty(getCellString(row, 7)));    // 列7=良品数量（实际发货量）
                    item.setDefectiveQty(parseQty(getCellString(row, 8))); // 列8=废品数量
                    item.setUnitPrice(parsePrice(getCellString(row, 10)));  // 列10=良品单价
                    item.setAmount(parsePrice(getCellString(row, 11)));     // 列11=良品金额（已算好）
                    item.setDetailRemark(getCellString(row, 16));

                    if (StringUtils.hasText(item.getMaterialName())) {
                        Long customerId = shipmentMap.get(shipmentNo).getCustomerId();
                        Long materialId = findOrCreateMaterialIdByName(item.getMaterialName(), item.getSpec(), customerId);
                        item.setMaterialId(materialId);
                        Material mat = materialMapper.selectById(materialId);
                        if (mat != null) {
                            item.setMaterialCode(mat.getMaterialCode());
                        }
                    }

                    if (StringUtils.hasText(item.getProcessName())) {
                        Long processId = findProcessIdByName(item.getProcessName());
                        if (processId != null) {
                            item.setProcessId(processId);
                        }
                    }

                    itemsMap.get(shipmentNo).add(item);

                } catch (Exception e) {
                    fail++;
                    errors.add("第" + (i + 1) + "行: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel解析失败: " + e.getMessage());
        }

        for (Map.Entry<String, Shipment> entry : shipmentMap.entrySet()) {
            String shipmentNo = entry.getKey();
            Shipment shipment = entry.getValue();
            try {
                getBaseMapper().insert(shipment);
                List<ShipmentItem> items = itemsMap.get(shipmentNo);
                shipmentItemService.saveItems(shipment.getId(), shipment.getShipmentNo(), items);
                success++;
            } catch (Exception e) {
                fail++;
                errors.add("单号" + shipmentNo + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("fail", fail);
        result.put("skip", skip);
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
                : new BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield new BigDecimal(cell.getNumericCellValue()).stripTrailingZeros().toPlainString(); }
                catch (Exception e) { yield cell.getStringCellValue().trim(); }
            }
            default -> "";
        };
    }

    /** 解析整数数量（四舍五入取整） */
    private BigDecimal parseQty(String s) {
        if (s == null || s.trim().isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()).setScale(0, java.math.RoundingMode.HALF_UP); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    /** 解析单价/金额（保留2位小数） */
    private BigDecimal parsePrice(String s) {
        if (s == null || s.trim().isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()).setScale(2, java.math.RoundingMode.HALF_UP); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private LocalDate parseExcelDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        value = value.trim();
        try {
            return LocalDate.parse(value.replace("/", "-").substring(0, 10));
        } catch (Exception e) {
            try {
                double d = Double.parseDouble(value);
                return LocalDate.of(1899, 12, 30).plusDays((long) d);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private BigDecimal parseBigDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Long findOrCreateCustomerIdByName(String customerName) {
        if (!StringUtils.hasText(customerName)) return null;
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Customer::getCustomerName, customerName).last("LIMIT 1");
        Customer customer = customerMapper.selectOne(wrapper);
        if (customer != null) {
            return customer.getId();
        } else {
            Customer newCustomer = new Customer();
            newCustomer.setCustomerName(customerName);
            newCustomer.setCustomerCode("AUTO_" + System.currentTimeMillis());
            newCustomer.setStatus(1);
            customerMapper.insert(newCustomer);
            return newCustomer.getId();
        }
    }

    private Long findOrCreateMaterialIdByName(String materialName, String spec, Long customerId) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Material::getMaterialName, materialName);
        if (customerId != null) {
            wrapper.eq(Material::getCustomerId, customerId);
        }
        if (StringUtils.hasText(spec)) {
            wrapper.eq(Material::getSpec, spec);
        }
        wrapper.last("LIMIT 1");
        Material material = materialMapper.selectOne(wrapper);
        if (material != null) {
            return material.getId();
        } else {
            LambdaQueryWrapper<Material> fuzzy = new LambdaQueryWrapper<>();
            fuzzy.eq(Material::getMaterialName, materialName).last("LIMIT 1");
            Material fallback = materialMapper.selectOne(fuzzy);
            if (fallback != null) return fallback.getId();

            Material newMaterial = new Material();
            newMaterial.setMaterialName(materialName);
            newMaterial.setSpec(spec);
            newMaterial.setCustomerId(customerId);
            newMaterial.setMaterialCode("AUTO_" + System.currentTimeMillis());
            newMaterial.setStatus(1);
            materialMapper.insert(newMaterial);
            return newMaterial.getId();
        }
    }

    private Long findProcessIdByName(String processName) {
        LambdaQueryWrapper<Process> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Process::getProcessName, processName).last("LIMIT 1");
        Process process = processMapper.selectOne(wrapper);
        return process != null ? process.getId() : null;
    }

    private boolean shipmentExists(String shipmentNo) {
        if (shipmentNo == null || shipmentNo.trim().isEmpty()) return false;
        return this.count(new LambdaQueryWrapper<Shipment>().eq(Shipment::getShipmentNo, shipmentNo.trim())) > 0;
    }

    @Override
    public void exportExcel(HttpServletResponse response, String keyword, Long customerId, String startDate, String endDate) {
        LambdaQueryWrapper<Shipment> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Shipment::getShipmentNo, keyword).or().like(Shipment::getCustomerName, keyword));
        }
        if (customerId != null) wrapper.eq(Shipment::getCustomerId, customerId);
        if (StringUtils.hasText(startDate)) wrapper.ge(Shipment::getShipmentDate, startDate);
        if (StringUtils.hasText(endDate)) wrapper.le(Shipment::getShipmentDate, endDate);
        wrapper.orderByDesc(Shipment::getShipmentDate).orderByDesc(Shipment::getId).last("LIMIT 5000");
        List<Shipment> shipments = this.list(wrapper);

        List<Long> ids = shipments.stream().map(Shipment::getId).collect(java.util.stream.Collectors.toList());
        List<ShipmentItem> allItems = ids.isEmpty() ? new ArrayList<>() :
            shipmentItemService.listByShipmentIds(ids);
        java.util.Map<Long, List<ShipmentItem>> itemMap = allItems.stream()
            .collect(java.util.stream.Collectors.groupingBy(ShipmentItem::getShipmentId));

        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("发货单");
        String[] headers = {"发货单号","发货日期","客户名称","制单人","备注","物料编码","物料名称","型号规格","工艺名称",
            "良品数量","废品数量","单价","金额","明细备注"};
        ExcelExportUtil.writeTitleRow(sheet, wb, "发货单", headers.length);
        ExcelExportUtil.writeHeaderRow(sheet, wb, headers);

        CellStyle masterS = ExcelExportUtil.masterRowStyle(wb);
        CellStyle masterDateS = ExcelExportUtil.masterRowDateStyle(wb);
        CellStyle s0 = ExcelExportUtil.dataStyle(wb, false);
        CellStyle s1 = ExcelExportUtil.dataStyle(wb, true);
        CellStyle n0 = ExcelExportUtil.numStyle(wb, false);
        CellStyle n1 = ExcelExportUtil.numStyle(wb, true);

        BigDecimal totalQty = BigDecimal.ZERO, totalDefQty = BigDecimal.ZERO, totalAmount = BigDecimal.ZERO;

        int rowIdx = 2;
        int detailCount = 0;
        for (Shipment s : shipments) {
            List<ShipmentItem> items = itemMap.getOrDefault(s.getId(), new ArrayList<>());
            Row masterRow = sheet.createRow(rowIdx++);
            ExcelExportUtil.setCell(masterRow, 0, s.getShipmentNo(), masterS);
            ExcelExportUtil.setCell(masterRow, 1, ExcelExportUtil.fmtDate(s.getShipmentDate()), masterDateS);
            ExcelExportUtil.setCell(masterRow, 2, s.getCustomerName(), masterS);
            ExcelExportUtil.setCell(masterRow, 3, s.getOperator(), masterS);
            ExcelExportUtil.setCell(masterRow, 4, s.getRemark(), masterS);
            for (int i = 5; i < headers.length; i++) ExcelExportUtil.setCell(masterRow, i, "", masterS);

            for (ShipmentItem item : items) {
                if (detailCount >= 50000) break;
                boolean even = (detailCount % 2 == 0);
                CellStyle cs = even ? s0 : s1;
                CellStyle ns = even ? n0 : n1;
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < 5; i++) ExcelExportUtil.setCell(row, i, "", cs);
                ExcelExportUtil.setCell(row, 5, item.getMaterialCode(), cs);
                ExcelExportUtil.setCell(row, 6, item.getMaterialName(), cs);
                ExcelExportUtil.setCell(row, 7, item.getSpec(), cs);
                ExcelExportUtil.setCell(row, 8, item.getProcessName(), cs);
                ExcelExportUtil.setCell(row, 9, item.getQuantity(), ns);
                ExcelExportUtil.setCell(row, 10, item.getDefectiveQty(), ns);
                ExcelExportUtil.setCell(row, 11, item.getUnitPrice(), ns);
                ExcelExportUtil.setCell(row, 12, item.getAmount(), ns);
                ExcelExportUtil.setCell(row, 13, item.getDetailRemark(), cs);
                if (item.getQuantity() != null) totalQty = totalQty.add(item.getQuantity());
                if (item.getDefectiveQty() != null) totalDefQty = totalDefQty.add(item.getDefectiveQty());
                if (item.getAmount() != null) totalAmount = totalAmount.add(item.getAmount());
                detailCount++;
            }
        }

        CellStyle sumS = ExcelExportUtil.summaryStyle(wb);
        CellStyle sumN = ExcelExportUtil.summaryNumStyle(wb);
        Row sumRow = sheet.createRow(rowIdx);
        ExcelExportUtil.setCell(sumRow, 0, "合计", sumS);
        for (int i = 1; i <= 8; i++) ExcelExportUtil.setCell(sumRow, i, "", sumS);
        ExcelExportUtil.setCell(sumRow, 9, totalQty, sumN);
        ExcelExportUtil.setCell(sumRow, 10, totalDefQty, sumN);
        ExcelExportUtil.setCell(sumRow, 11, "", sumS);
        ExcelExportUtil.setCell(sumRow, 12, totalAmount, sumN);
        ExcelExportUtil.setCell(sumRow, 13, "", sumS);

        sheet.createFreezePane(0, 2);
        ExcelExportUtil.autoSize(sheet, headers.length);
        try {
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            ExcelExportUtil.writeResponse(wb, response, "发货单_" + today + ".xlsx");
        } catch (IOException e) {
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }

    /**
     * 将发货明细中的单价同步回物料默认单价，同时 upsert 工艺价格表。
     */
    private void syncMaterialPrices(List<ShipmentItem> items, Long customerId, String customerName) {
        if (items == null) return;
        for (ShipmentItem item : items) {
            if (item.getMaterialId() == null) continue;
            if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) continue;
            Material mat = materialMapper.selectById(item.getMaterialId());
            if (mat == null) continue;
            if (mat.getDefaultPrice() == null || mat.getDefaultPrice().compareTo(item.getUnitPrice()) != 0) {
                mat.setDefaultPrice(item.getUnitPrice());
                materialMapper.updateById(mat);
            }
            // 同步 upsert 工艺价格表
            if (item.getProcessId() != null) {
                materialProcessPriceService.upsertPrice(
                    customerId, customerName,
                    item.getMaterialId(), item.getMaterialName(), item.getMaterialCode(), item.getSpec(),
                    item.getProcessId(), item.getProcessName(), item.getUnitPrice()
                );
            }
        }
    }
}
