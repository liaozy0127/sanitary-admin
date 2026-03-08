package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Receipt;
import com.sanitary.admin.entity.ReceiptItem;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ReceiptMapper;
import com.sanitary.admin.mapper.ProcessMapper;
import com.sanitary.admin.entity.Process;
import com.sanitary.admin.service.InventoryService;
import com.sanitary.admin.service.ReceiptItemService;
import com.sanitary.admin.service.ReceiptService;
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
    private final ReceiptItemService receiptItemService;

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
        wrapper.orderByDesc(Receipt::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Receipt createReceipt(Receipt receipt) {
        receipt.setReceiptNo(generateNoUtil.generate("RH", "receipt", "receipt_no"));
        if (receipt.getStatus() == null) {
            receipt.setStatus(1);
        }
        save(receipt);

        if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
            receiptItemService.saveItems(receipt.getId(), receipt.getReceiptNo(), receipt.getItems());
        }

        return receipt;
    }

    @Override
    @Transactional
    public Receipt updateReceipt(Receipt receipt) {
        // 先删除旧的明细
        receiptItemService.deleteByReceiptId(receipt.getId());
        
        // 更新主表
        updateById(receipt);
        
        // 保存新的明细
        if (receipt.getItems() != null && !receipt.getItems().isEmpty()) {
            receiptItemService.saveItems(receipt.getId(), receipt.getReceiptNo(), receipt.getItems());
        }
        
        return receipt;
    }

    @Override
    @Transactional
    public boolean deleteReceipt(Long id) {
        // 先删除明细
        receiptItemService.deleteByReceiptId(id);
        
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
                    item.setQuantity(parseBigDecimal(getCellString(row, 8)));
                    item.setShippedQty(parseBigDecimal(getCellString(row, 9)));
                    item.setUnshippedQty(parseBigDecimal(getCellString(row, 10)));
                    item.setUnitPrice(parseBigDecimal(getCellString(row, 11)));
                    item.setCustomerOrderNo(getCellString(row, 12));
                    item.setDetailRemark(getCellString(row, 14));
                    item.setPlannedQty(parseBigDecimal(getCellString(row, 15)));
                    item.setWareHousedQty(parseBigDecimal(getCellString(row, 16)));
                    item.setUnwareHousedQty(parseBigDecimal(getCellString(row, 18)));

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
                : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf((long) cell.getNumericCellValue()); }
                catch (Exception e) { yield cell.getStringCellValue().trim(); }
            }
            default -> "";
        };
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
}
