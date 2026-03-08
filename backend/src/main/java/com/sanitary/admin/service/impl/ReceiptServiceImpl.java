package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Receipt;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ReceiptMapper;
import com.sanitary.admin.mapper.ProcessMapper;
import com.sanitary.admin.entity.Process;
import com.sanitary.admin.service.InventoryService;
import com.sanitary.admin.service.ReceiptService;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
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

    @Override
    public Page<Receipt> pageList(int page, int size, String keyword, Long customerId,
                                  String startDate, String endDate) {
        LambdaQueryWrapper<Receipt> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Receipt::getReceiptNo, keyword)
                    .or().like(Receipt::getCustomerName, keyword)
                    .or().like(Receipt::getMaterialName, keyword));
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
        if (receipt.getQuantity() != null && receipt.getUnitPrice() != null) {
            receipt.setAmount(receipt.getQuantity().multiply(receipt.getUnitPrice()));
        }
        save(receipt);
        // Price memory: update material default_price
        if (receipt.getMaterialId() != null && receipt.getUnitPrice() != null
                && receipt.getUnitPrice().compareTo(BigDecimal.ZERO) > 0) {
            Material material = materialMapper.selectById(receipt.getMaterialId());
            if (material != null) {
                material.setDefaultPrice(receipt.getUnitPrice());
                materialMapper.updateById(material);
            }
        }

        // Update inventory
        inventoryService.updateInventory(
                receipt.getMaterialId(),
                receipt.getCustomerId(),
                receipt.getProcessId(),
                receipt.getMaterialCode(),
                receipt.getMaterialName(),
                receipt.getCustomerName(),
                receipt.getSpec(),
                receipt.getProcessName(),
                receipt.getQuantity(),
                1,  // 收货
                "RECEIPT",
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getReceiptDate()
        );

        return receipt;
    }

    @Override
    @Transactional
    public Map<String, Object> importExcel(MultipartFile file, String mode) {
        int success = 0;
        int fail = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();

        String lastReceiptNo = ""; // Track the last receipt number for continuation

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String receiptNo = getCellString(row, 1); // 1-based index for receipt_no

                    // Handle empty receipt number by continuing from previous row
                    if (receiptNo.isEmpty()) {
                        receiptNo = lastReceiptNo;
                    } else {
                        lastReceiptNo = receiptNo;
                    }

                    // Check for duplicates (idempotency)
                    if (receiptExists(receiptNo)) {
                        skip++;
                        continue;
                    }

                    Receipt receipt = new Receipt();
                    receipt.setReceiptNo(receiptNo);
                    receipt.setReceiptDate(parseExcelDate(getCellString(row, 2))); // 2-based index for date
                    receipt.setCustomerName(getCellString(row, 3)); // 3-based index for customer
                    receipt.setMaterialName(getCellString(row, 4)); // 4-based index for material
                    receipt.setSpec(getCellString(row, 5)); // 5-based index for spec
                    receipt.setProcessName(getCellString(row, 6)); // 6-based index for process
                    receipt.setReceiptSource(getCellString(row, 7)); // 7-based index for receipt source
                    receipt.setQuantity(parseBigDecimal(getCellString(row, 8))); // 8-based index for quantity
                    receipt.setUnitPrice(parseBigDecimal(getCellString(row, 11))); // 11-based index for unit price
                    receipt.setCustomerOrderNo(getCellString(row, 12)); // 12-based index for customer order
                    receipt.setRemark(getCellString(row, 13)); // 13-based index for remark
                    receipt.setDetailRemark(getCellString(row, 14)); // 14-based index for detail remark

                    // 根据客户名称查找客户ID
                    Long customerId = findOrCreateCustomerIdByName(receipt.getCustomerName());
                    receipt.setCustomerId(customerId);

                    // 根据物料名称查找物料ID
                    Long materialId = findOrCreateMaterialIdByName(receipt.getMaterialName(), receipt.getSpec(), customerId);
                    receipt.setMaterialId(materialId);

                    // 根据工艺名称查找工艺ID
                    if (receipt.getProcessName() != null && !receipt.getProcessName().trim().isEmpty()) {
                        Long processId = findProcessIdByName(receipt.getProcessName());
                        if (processId != null) {
                            receipt.setProcessId(processId);
                        }
                    }

                    if (receipt.getQuantity() != null && receipt.getUnitPrice() != null) {
                        receipt.setAmount(receipt.getQuantity().multiply(receipt.getUnitPrice()));
                    }
                    receipt.setStatus(1);
                    save(receipt);

                    // Update inventory only if mode is not "history"
                    if (!"history".equals(mode)) {
                        inventoryService.updateInventory(
                                receipt.getMaterialId(),
                                receipt.getCustomerId(),
                                receipt.getProcessId(),
                                receipt.getMaterialCode(), // 注意：这里可能会为空，但我们使用物料名称
                                receipt.getMaterialName(),
                                receipt.getCustomerName(),
                                receipt.getSpec(),
                                receipt.getProcessName(),
                                receipt.getQuantity(),
                                1,  // 收货
                                "RECEIPT",
                                receipt.getId(),
                                receipt.getReceiptNo(),
                                receipt.getReceiptDate()
                        );
                    }

                    success++;
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
    public void exportTemplate(HttpServletResponse response) {
        try {
            XSSFWorkbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("收货单导入模板");
            Row header = sheet.createRow(0);
            String[] columns = {"收货日期(yyyy-MM-dd)", "客户名称", "物料名称", "规格", "工艺", "数量", "单价", "备注"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns[i]);
                sheet.setColumnWidth(i, 5000);
            }
            // Sample row
            Row sample = sheet.createRow(1);
            sample.createCell(0).setCellValue(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            sample.createCell(1).setCellValue("示例客户");
            sample.createCell(2).setCellValue("示例物料");
            sample.createCell(3).setCellValue("100x200");
            sample.createCell(4).setCellValue("电镀");
            sample.createCell(5).setCellValue("100");
            sample.createCell(6).setCellValue("5.00");
            sample.createCell(7).setCellValue("备注");

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
                return null; // 日期解析失败，不中断
            }
        }
    }

    private BigDecimal parseBigDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private Long findOrCreateCustomerIdByName(String customerName) {
        // 根据客户名称查找客户ID
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Customer::getCustomerName, customerName);

        Customer customer = customerMapper.selectOne(wrapper);
        if (customer != null) {
            return customer.getId();
        } else {
            // 如果客户不存在，创建一个新的客户
            Customer newCustomer = new Customer();
            newCustomer.setCustomerName(customerName);
            newCustomer.setCustomerCode("AUTO_" + System.currentTimeMillis()); // 自动生成编码
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
        if (spec != null && !spec.trim().isEmpty()) {
            wrapper.eq(Material::getSpec, spec);
        }
        wrapper.last("LIMIT 1"); // 防止多条时报错，取第一条

        Material material = materialMapper.selectOne(wrapper);
        if (material != null) {
            return material.getId();
        } else {
            // 物料不存在则按名称模糊匹配（不带客户限制）
            LambdaQueryWrapper<Material> fuzzy = new LambdaQueryWrapper<>();
            fuzzy.eq(Material::getMaterialName, materialName).last("LIMIT 1");
            Material fallback = materialMapper.selectOne(fuzzy);
            if (fallback != null) return fallback.getId();
            // 兜底：创建新物料
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
        // 根据工艺名称查找工艺ID
        LambdaQueryWrapper<Process> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Process::getProcessName, processName);

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
