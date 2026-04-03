package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Receipt;
import com.sanitary.admin.entity.ReceiptItem;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.mapper.InventoryMapper;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ReceiptMapper;
import com.sanitary.admin.mapper.ProcessMapper;
import com.sanitary.admin.entity.Process;
import com.sanitary.admin.service.MaterialProcessPriceService;
import com.sanitary.admin.service.InventoryService;
import com.sanitary.admin.service.ReceiptItemService;
import com.sanitary.admin.service.ReceiptService;
import com.sanitary.admin.util.ExcelExportUtil;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl extends ServiceImpl<ReceiptMapper, Receipt> implements ReceiptService {

    private final GenerateNoUtil generateNoUtil;
    private final MaterialMapper materialMapper;
    private final CustomerMapper customerMapper;
    private final ProcessMapper processMapper;
    private final InventoryService inventoryService;
    private final InventoryMapper inventoryMapper;
    private final ReceiptItemService receiptItemService;
    private final MaterialProcessPriceService materialProcessPriceService;

    @Override
    public Page<Receipt> pageList(int page, int size, String keyword, Long customerId,
                                  String startDate, String endDate) {
        LambdaQueryWrapper<Receipt> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Receipt::getReceiptNo, keyword)
                    .or().like(Receipt::getCustomerName, keyword));
        }
        if (customerId != null) {
            wrapper.eq(Receipt::getCustomerId, customerId);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(Receipt::getReceiptDate, LocalDate.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(Receipt::getReceiptDate, LocalDate.parse(endDate));
        }
        wrapper.orderByDesc(Receipt::getReceiptDate).orderByDesc(Receipt::getId);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Receipt createReceipt(Receipt receipt) {
        receipt.setReceiptNo(generateNoUtil.generate("SH", "receipt", "receipt_no"));
        if (receipt.getStatus() == null) {
            receipt.setStatus(1);
        }
        save(receipt);

        if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
            receiptItemService.saveItems(receipt.getId(), receipt.getReceiptNo(), receipt.getItems());

            // 更新库存 - 收货入库
            for (ReceiptItem item : receipt.getItems()) {
                inventoryService.updateInventory(
                    item.getMaterialId(),
                    receipt.getCustomerId(),
                    item.getProcessId(),
                    item.getMaterialCode(),
                    item.getMaterialName(),
                    receipt.getCustomerName(),
                    item.getSpec(),
                    item.getProcessName(),
                    item.getQuantity(),
                    1,  // changeType: 1=收货(入库)
                    "receipt",  // orderType
                    receipt.getId(),
                    receipt.getReceiptNo(),
                    receipt.getReceiptDate()
                );
                // 返工收货：实时增加返工库存
                if ("返工".equals(item.getReceiptSource()) && item.getQuantity() != null
                        && item.getQuantity().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    Long effectivePid = item.getProcessId() != null ? item.getProcessId() : 0L;
                    inventoryMapper.incrementReworkQty(item.getMaterialId(), receipt.getCustomerId(), effectivePid, item.getQuantity());
                }
                // 同步更新物料默认单价（非返工来源且单价>0时覆盖，保持价格最新）
                syncMaterialPrice(item, receipt.getCustomerId(), receipt.getCustomerName(), receipt.getReceiptDate());
                // 同步物料规格到物料档案
                syncMaterialSpec(item);
            }
        }

        return receipt;
    }

    @Override
    @Transactional
    public Receipt updateReceipt(Receipt receipt) {
        // 若请求未传 receiptNo，从数据库补充（避免明细插入时 NOT NULL 约束报错）
        if (receipt.getReceiptNo() == null) {
            Receipt existing = getById(receipt.getId());
            if (existing != null) {
                receipt.setReceiptNo(existing.getReceiptNo());
            }
        }

        // 先查询旧的明细，用于冲销库存
        List<ReceiptItem> oldItems = receiptItemService.listByReceiptId(receipt.getId());

        // 先删除旧的明细
        receiptItemService.deleteByReceiptId(receipt.getId());

        // 更新主表
        updateById(receipt);

        // 保存新的明细
        if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
            receiptItemService.saveItems(receipt.getId(), receipt.getReceiptNo(), receipt.getItems());
        }
        
        // 冲销旧库存（反向操作）
        for (ReceiptItem oldItem : oldItems) {
            inventoryService.updateInventory(
                oldItem.getMaterialId(),
                receipt.getCustomerId(),
                oldItem.getProcessId(),
                oldItem.getMaterialCode(),
                oldItem.getMaterialName(),
                receipt.getCustomerName(),
                oldItem.getSpec(),
                oldItem.getProcessName(),
                oldItem.getQuantity().negate(), // 反向冲销，数量取负
                1,  // changeType: 1=收货(入库)
                "receipt",  // orderType
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getReceiptDate()
            );
            // 冲销旧返工库存
            if ("返工".equals(oldItem.getReceiptSource()) && oldItem.getQuantity() != null
                    && oldItem.getQuantity().compareTo(java.math.BigDecimal.ZERO) > 0) {
                Long effectivePid = oldItem.getProcessId() != null ? oldItem.getProcessId() : 0L;
                inventoryMapper.incrementReworkQty(oldItem.getMaterialId(), receipt.getCustomerId(), effectivePid,
                        oldItem.getQuantity().negate());
            }
        }

        // 更新新库存
        if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
            for (ReceiptItem item : receipt.getItems()) {
                inventoryService.updateInventory(
                    item.getMaterialId(),
                    receipt.getCustomerId(),
                    item.getProcessId(),
                    item.getMaterialCode(),
                    item.getMaterialName(),
                    receipt.getCustomerName(),
                    item.getSpec(),
                    item.getProcessName(),
                    item.getQuantity(),
                    1,  // changeType: 1=收货(入库)
                    "receipt",  // orderType
                    receipt.getId(),
                    receipt.getReceiptNo(),
                    receipt.getReceiptDate()
                );
                // 新返工收货：增加返工库存
                if ("返工".equals(item.getReceiptSource()) && item.getQuantity() != null
                        && item.getQuantity().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    Long effectivePid = item.getProcessId() != null ? item.getProcessId() : 0L;
                    inventoryMapper.incrementReworkQty(item.getMaterialId(), receipt.getCustomerId(), effectivePid, item.getQuantity());
                }
                // 同步更新物料默认单价（非返工来源且单价>0时覆盖，保持价格最新）
                syncMaterialPrice(item, receipt.getCustomerId(), receipt.getCustomerName(), receipt.getReceiptDate());
                // 同步物料规格到物料档案
                syncMaterialSpec(item);
            }
        }

        return receipt;
    }

    @Override
    @Transactional
    public boolean deleteReceipt(Long id) {
        // 查询明细，用于冲销库存
        List<ReceiptItem> items = receiptItemService.listByReceiptId(id);
        // 获取收货单信息
        Receipt receipt = getById(id);
        
        // 先删除明细
        receiptItemService.deleteByReceiptId(id);
        
        // 冲销库存（反向操作）
        for (ReceiptItem item : items) {
            inventoryService.updateInventory(
                item.getMaterialId(),
                receipt.getCustomerId(),
                item.getProcessId(),
                item.getMaterialCode(),
                item.getMaterialName(),
                receipt.getCustomerName(),
                item.getSpec(),
                item.getProcessName(),
                item.getQuantity().negate(), // 反向冲销，数量取负
                1,  // changeType: 1=收货(入库)
                "receipt",  // orderType
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getReceiptDate()
            );
            // 冲销返工库存
            if ("返工".equals(item.getReceiptSource()) && item.getQuantity() != null
                    && item.getQuantity().compareTo(java.math.BigDecimal.ZERO) > 0) {
                Long effectivePid = item.getProcessId() != null ? item.getProcessId() : 0L;
                inventoryMapper.incrementReworkQty(item.getMaterialId(), receipt.getCustomerId(), effectivePid,
                        item.getQuantity().negate());
            }
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

        Map<String, Receipt> receiptMap = new LinkedHashMap<>();
        Map<String, List<ReceiptItem>> itemsMap = new LinkedHashMap<>();

        String lastReceiptNo = "";

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String receiptNo = getCellString(row, 1);
                    if (receiptNo.isEmpty()) {
                        receiptNo = lastReceiptNo;
                    } else {
                        lastReceiptNo = receiptNo;
                    }

                    if (receiptNo.isEmpty()) {
                        skip++;
                        continue;
                    }

                    // Skip if already exists in DB (idempotency - skip entire order)
                    if (receiptExists(receiptNo)) {
                        skip++;
                        continue;
                    }

                    // Build master record (only on first occurrence of this receiptNo)
                    if (!receiptMap.containsKey(receiptNo)) {
                        Receipt receipt = new Receipt();
                        receipt.setReceiptNo(receiptNo);
                        receipt.setReceiptDate(parseExcelDate(getCellString(row, 2)));
                        receipt.setCustomerName(getCellString(row, 3));
                        receipt.setRemark(getCellString(row, 13));
                        receipt.setStatus(1);

                        Long customerId = findOrCreateCustomerIdByName(receipt.getCustomerName());
                        receipt.setCustomerId(customerId);

                        receiptMap.put(receiptNo, receipt);
                        itemsMap.put(receiptNo, new ArrayList<>());
                    }

                    // Build item record for this row
                    ReceiptItem item = new ReceiptItem();
                    item.setReceiptNo(receiptNo);
                    item.setMaterialName(getCellString(row, 4));
                    item.setSpec(getCellString(row, 5));
                    item.setProcessName(getCellString(row, 6));
                    item.setReceiptSource(getCellString(row, 7));
                    item.setQuantity(parseQty(getCellString(row, 8)));
                    item.setShippedQty(parseQty(getCellString(row, 9)));
                    item.setUnshippedQty(parseQty(getCellString(row, 10)));
                    item.setUnitPrice(parsePrice(getCellString(row, 11)));
                    item.setCustomerOrderNo(getCellString(row, 12));
                    item.setDetailRemark(getCellString(row, 14));
                    item.setPlannedQty(parseQty(getCellString(row, 15)));
                    item.setWareHousedQty(parseQty(getCellString(row, 16)));
                    item.setUnwareHousedQty(parseQty(getCellString(row, 18)));

                    if (item.getUnitPrice() != null && item.getQuantity() != null) {
                        item.setAmount(item.getQuantity().multiply(item.getUnitPrice()));
                    }

                    // Look up material ID
                    if (StringUtils.hasText(item.getMaterialName())) {
                        Long customerId = receiptMap.get(receiptNo).getCustomerId();
                        Long materialId = findOrCreateMaterialIdByName(item.getMaterialName(), item.getSpec(), customerId);
                        item.setMaterialId(materialId);
                        Material mat = materialMapper.selectById(materialId);
                        if (mat != null) {
                            item.setMaterialCode(mat.getMaterialCode());
                            // 若物料单价为空或0，用收货单单价回填（过滤异常值，限10000以内）
                            if (item.getUnitPrice() != null
                                    && item.getUnitPrice().compareTo(java.math.BigDecimal.ZERO) > 0
                                    && item.getUnitPrice().compareTo(new java.math.BigDecimal("10000")) <= 0
                                    && (mat.getDefaultPrice() == null
                                        || mat.getDefaultPrice().compareTo(java.math.BigDecimal.ZERO) == 0)) {
                                mat.setDefaultPrice(item.getUnitPrice());
                                materialMapper.updateById(mat);
                            }
                        }
                    }

                    // Look up process ID
                    if (StringUtils.hasText(item.getProcessName())) {
                        Long processId = findProcessIdByName(item.getProcessName());
                        if (processId != null) {
                            item.setProcessId(processId);
                        }
                    }

                    itemsMap.get(receiptNo).add(item);

                } catch (Exception e) {
                    fail++;
                    errors.add("第" + (i + 1) + "行: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel解析失败: " + e.getMessage());
        }

        // Batch save all receipts and their items
        for (Map.Entry<String, Receipt> entry : receiptMap.entrySet()) {
            String receiptNo = entry.getKey();
            Receipt receipt = entry.getValue();
            try {
                getBaseMapper().insert(receipt);
                List<ReceiptItem> items = itemsMap.get(receiptNo);
                receiptItemService.saveItems(receipt.getId(), receipt.getReceiptNo(), items);
                success++;
            } catch (Exception e) {
                fail++;
                errors.add("单号" + receiptNo + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("fail", fail);
        result.put("skip", skip);
        result.put("errors", errors);
        return result;
    }

    @Override
    public void exportTemplate(HttpServletResponse response) {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("收货单导入模板");
            Row header = sheet.createRow(0);
            String[] columns = {"序号", "收货单号", "收货日期", "客户名称", "产品名称", "型号规格", "工艺名称",
                    "收货来源", "收货数量", "发货数量", "未发货数量", "单价", "客户单号", "备注", "明细备注",
                    "排产数量", "入库数量", "（忽略）", "未入库数量"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                sheet.setColumnWidth(i, 4000);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            String filename = URLEncoder.encode("收货单导入模板.xlsx", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment;filename=" + filename);
            workbook.write(response.getOutputStream());
            workbook.close();
        } catch (IOException e) {
            throw new RuntimeException("模板生成失败: " + e.getMessage());
        }
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
        if (process != null) {
            return process.getId();
        }
        return null;
    }

    private boolean receiptExists(String receiptNo) {
        if (receiptNo == null || receiptNo.trim().isEmpty()) return false;
        return this.count(new LambdaQueryWrapper<Receipt>().eq(Receipt::getReceiptNo, receiptNo.trim())) > 0;
    }

    /**
     * 将收货明细的单价同步回物料档案默认单价，同时 upsert 工艺价格表。
     * 条件：非返工来源 且 单价 > 0 且 materialId 有值。
     */
    private void syncMaterialPrice(ReceiptItem item, Long customerId, String customerName, LocalDate receiptDate) {        if (item.getMaterialId() == null) return;
        if (item.getUnitPrice() == null || item.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0) return;
        if ("返工".equals(item.getReceiptSource())) return;
        Material mat = materialMapper.selectById(item.getMaterialId());
        if (mat != null && item.getUnitPrice().compareTo(mat.getDefaultPrice() != null ? mat.getDefaultPrice() : BigDecimal.ZERO) != 0) {
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

    /** 若收货明细中的规格与物料档案不一致，则同步更新物料档案的规格。 */
    private void syncMaterialSpec(ReceiptItem item) {
        if (item.getMaterialId() == null) return;
        String newSpec = item.getSpec();
        if (newSpec == null) return;
        Material mat = materialMapper.selectById(item.getMaterialId());
        if (mat == null) return;
        if (!newSpec.equals(mat.getSpec())) {
            mat.setSpec(newSpec);
            materialMapper.updateById(mat);
        }
    }

    @Override
    public void exportExcel(HttpServletResponse response, String keyword, Long customerId,
                            String startDate, String endDate) {
        LambdaQueryWrapper<Receipt> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Receipt::getReceiptNo, keyword).or().like(Receipt::getCustomerName, keyword));
        }
        if (customerId != null) wrapper.eq(Receipt::getCustomerId, customerId);
        if (StringUtils.hasText(startDate)) wrapper.ge(Receipt::getReceiptDate, startDate);
        if (StringUtils.hasText(endDate)) wrapper.le(Receipt::getReceiptDate, endDate);
        wrapper.orderByDesc(Receipt::getReceiptDate).orderByDesc(Receipt::getId).last("LIMIT 5000");
        List<Receipt> receipts = this.list(wrapper);

        List<Long> ids = receipts.stream().map(Receipt::getId).collect(java.util.stream.Collectors.toList());
        List<ReceiptItem> allItems = ids.isEmpty() ? new ArrayList<>() :
            receiptItemService.listByReceiptIds(ids);
        java.util.Map<Long, List<ReceiptItem>> itemMap = allItems.stream()
            .collect(java.util.stream.Collectors.groupingBy(ReceiptItem::getReceiptId));

        org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("收货单");
        String[] headers = {"收货单号","收货日期","客户名称","备注","物料编码","物料名称","型号规格","工艺名称",
            "收货来源","收货数量","发货数量","未发货数量","排产数量","入库数量","未入库数量","单价","金额","客户单号","明细备注"};
        ExcelExportUtil.writeTitleRow(sheet, wb, "收货单", headers.length);
        ExcelExportUtil.writeHeaderRow(sheet, wb, headers);

        org.apache.poi.ss.usermodel.CellStyle masterS = ExcelExportUtil.masterRowStyle(wb);
        org.apache.poi.ss.usermodel.CellStyle masterDateS = ExcelExportUtil.masterRowDateStyle(wb);
        org.apache.poi.ss.usermodel.CellStyle s0 = ExcelExportUtil.dataStyle(wb, false);
        org.apache.poi.ss.usermodel.CellStyle s1 = ExcelExportUtil.dataStyle(wb, true);
        org.apache.poi.ss.usermodel.CellStyle n0 = ExcelExportUtil.numStyle(wb, false);
        org.apache.poi.ss.usermodel.CellStyle n1 = ExcelExportUtil.numStyle(wb, true);
        org.apache.poi.ss.usermodel.CellStyle q0 = ExcelExportUtil.qtyStyle(wb, false);
        org.apache.poi.ss.usermodel.CellStyle q1 = ExcelExportUtil.qtyStyle(wb, true);

        // 合计累加器
        BigDecimal totalQty = BigDecimal.ZERO, totalShipped = BigDecimal.ZERO,
            totalUnshipped = BigDecimal.ZERO, totalPlanned = BigDecimal.ZERO,
            totalWarehoused = BigDecimal.ZERO, totalUnwarehoused = BigDecimal.ZERO,
            totalAmount = BigDecimal.ZERO;

        int rowIdx = 2;
        int detailCount = 0;
        for (Receipt r : receipts) {
            List<ReceiptItem> items = itemMap.getOrDefault(r.getId(), new ArrayList<>());
            // 主单行
            org.apache.poi.ss.usermodel.Row masterRow = sheet.createRow(rowIdx++);
            ExcelExportUtil.setCell(masterRow, 0, r.getReceiptNo(), masterS);
            ExcelExportUtil.setCell(masterRow, 1, ExcelExportUtil.fmtDate(r.getReceiptDate()), masterDateS);
            ExcelExportUtil.setCell(masterRow, 2, r.getCustomerName(), masterS);
            ExcelExportUtil.setCell(masterRow, 3, r.getRemark(), masterS);
            for (int i = 4; i < headers.length; i++) ExcelExportUtil.setCell(masterRow, i, "", masterS);

            // 明细行
            for (ReceiptItem item : items) {
                if (detailCount >= 50000) break;
                boolean even = (detailCount % 2 == 0);
                org.apache.poi.ss.usermodel.CellStyle s = even ? s0 : s1;
                org.apache.poi.ss.usermodel.CellStyle ns = even ? n0 : n1;
                org.apache.poi.ss.usermodel.CellStyle qs = even ? q0 : q1;
                org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < 4; i++) ExcelExportUtil.setCell(row, i, "", s);
                ExcelExportUtil.setCell(row, 4, item.getMaterialCode(), s);
                ExcelExportUtil.setCell(row, 5, item.getMaterialName(), s);
                ExcelExportUtil.setCell(row, 6, item.getSpec(), s);
                ExcelExportUtil.setCell(row, 7, item.getProcessName(), s);
                ExcelExportUtil.setCell(row, 8, item.getReceiptSource(), s);
                ExcelExportUtil.setCell(row, 9, item.getQuantity(), qs);
                ExcelExportUtil.setCell(row, 10, item.getShippedQty(), qs);
                ExcelExportUtil.setCell(row, 11, item.getUnshippedQty(), qs);
                ExcelExportUtil.setCell(row, 12, item.getPlannedQty(), qs);
                ExcelExportUtil.setCell(row, 13, item.getWareHousedQty(), qs);
                ExcelExportUtil.setCell(row, 14, item.getUnwareHousedQty(), qs);
                ExcelExportUtil.setCell(row, 15, item.getUnitPrice(), ns);
                ExcelExportUtil.setCell(row, 16, item.getAmount(), ns);
                ExcelExportUtil.setCell(row, 17, item.getCustomerOrderNo(), s);
                ExcelExportUtil.setCell(row, 18, item.getDetailRemark(), s);
                // 累计
                if (item.getQuantity() != null) totalQty = totalQty.add(item.getQuantity());
                if (item.getShippedQty() != null) totalShipped = totalShipped.add(item.getShippedQty());
                if (item.getUnshippedQty() != null) totalUnshipped = totalUnshipped.add(item.getUnshippedQty());
                if (item.getPlannedQty() != null) totalPlanned = totalPlanned.add(item.getPlannedQty());
                if (item.getWareHousedQty() != null) totalWarehoused = totalWarehoused.add(item.getWareHousedQty());
                if (item.getUnwareHousedQty() != null) totalUnwarehoused = totalUnwarehoused.add(item.getUnwareHousedQty());
                if (item.getAmount() != null) totalAmount = totalAmount.add(item.getAmount());
                detailCount++;
            }
        }

        // 合计行
        org.apache.poi.ss.usermodel.CellStyle sumS = ExcelExportUtil.summaryStyle(wb);
        org.apache.poi.ss.usermodel.CellStyle sumN = ExcelExportUtil.summaryNumStyle(wb);
        org.apache.poi.ss.usermodel.CellStyle sumQ = ExcelExportUtil.summaryQtyStyle(wb);
        org.apache.poi.ss.usermodel.Row sumRow = sheet.createRow(rowIdx);
        ExcelExportUtil.setCell(sumRow, 0, "合计", sumS);
        for (int i = 1; i <= 8; i++) ExcelExportUtil.setCell(sumRow, i, "", sumS);
        ExcelExportUtil.setCell(sumRow, 9, totalQty, sumQ);
        ExcelExportUtil.setCell(sumRow, 10, totalShipped, sumQ);
        ExcelExportUtil.setCell(sumRow, 11, totalUnshipped, sumQ);
        ExcelExportUtil.setCell(sumRow, 12, totalPlanned, sumQ);
        ExcelExportUtil.setCell(sumRow, 13, totalWarehoused, sumQ);
        ExcelExportUtil.setCell(sumRow, 14, totalUnwarehoused, sumQ);
        ExcelExportUtil.setCell(sumRow, 15, BigDecimal.ZERO, sumN);
        ExcelExportUtil.setCell(sumRow, 16, totalAmount, sumN);
        ExcelExportUtil.setCell(sumRow, 17, "", sumS);
        ExcelExportUtil.setCell(sumRow, 18, "", sumS);

        sheet.createFreezePane(0, 2);
        ExcelExportUtil.autoSize(sheet, headers.length);
        try {
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            ExcelExportUtil.writeResponse(wb, response, "收货单_" + today + ".xlsx");
        } catch (IOException e) {
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }
}
