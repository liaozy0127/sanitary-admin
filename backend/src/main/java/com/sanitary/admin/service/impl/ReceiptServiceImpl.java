package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Receipt;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ReceiptMapper;
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
        return receipt;
    }

    @Override
    @Transactional
    public Map<String, Object> importExcel(MultipartFile file) {
        int success = 0;
        int fail = 0;
        List<String> errors = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    Receipt receipt = new Receipt();
                    receipt.setReceiptDate(parseDate(getCellString(row, 0)));
                    receipt.setCustomerName(getCellString(row, 1));
                    receipt.setMaterialName(getCellString(row, 2));
                    receipt.setSpec(getCellString(row, 3));
                    receipt.setProcessName(getCellString(row, 4));
                    receipt.setQuantity(parseBigDecimal(getCellString(row, 5)));
                    receipt.setUnitPrice(parseBigDecimal(getCellString(row, 6)));
                    receipt.setRemark(getCellString(row, 7));
                    receipt.setCustomerId(0L);
                    receipt.setMaterialId(0L);
                    if (receipt.getQuantity() != null && receipt.getUnitPrice() != null) {
                        receipt.setAmount(receipt.getQuantity().multiply(receipt.getUnitPrice()));
                    }
                    receipt.setReceiptNo(generateNoUtil.generate("RH", "receipt", "receipt_no"));
                    receipt.setStatus(1);
                    save(receipt);
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
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                } else {
                    yield String.valueOf((long) cell.getNumericCellValue());
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private LocalDate parseDate(String s) {
        if (!StringUtils.hasText(s)) return LocalDate.now();
        return LocalDate.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private BigDecimal parseBigDecimal(String s) {
        if (!StringUtils.hasText(s)) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
