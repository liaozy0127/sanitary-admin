package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    @Override
    public Page<Customer> pageList(int page, int size, String keyword, String customerType) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Customer::getCustomerName, keyword)
                    .or().like(Customer::getCustomerCode, keyword));
        }
        if (StringUtils.hasText(customerType)) {
            wrapper.eq(Customer::getCustomerType, customerType);
        }
        wrapper.orderByDesc(Customer::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public List<Map<String, Object>> listAll() {
        return list(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getStatus, 1)
                .select(Customer::getId, Customer::getCustomerName)
                .orderByAsc(Customer::getCustomerCode))
                .stream()
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getCustomerName());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> listAllByType(String customerType) {
        return list(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getStatus, 1)
                .eq(StringUtils.hasText(customerType), Customer::getCustomerType, customerType)
                .select(Customer::getId, Customer::getCustomerName)
                .orderByAsc(Customer::getCustomerCode))
                .stream()
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getCustomerName());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> importFromExcel(MultipartFile file) {
        int success = 0;
        int fail = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();

        try (java.io.InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String customerCode = getCellString(row, 0); // 客户代码
                    if (customerCode.isEmpty()) {
                        fail++;
                        errors.add("第" + (i + 1) + "行: 客户代码不能为空");
                        continue;
                    }

                    // Check for duplicates (idempotency)
                    if (customerExists(customerCode)) {
                        skip++;
                        continue;
                    }

                    Customer customer = new Customer();
                    customer.setCustomerCode(customerCode);
                    customer.setCustomerName(getCellString(row, 1)); // 客户名称
                    customer.setCustomerType(getCellString(row, 2)); // 客户类型
                    customer.setSalesperson(getCellString(row, 3)); // 业务员
                    customer.setContactPerson(getCellString(row, 4)); // 联系人
                    customer.setContactPhone(getCellString(row, 5)); // 联系电话
                    customer.setAddress(getCellString(row, 6)); // 地址
                    customer.setBankName(getCellString(row, 7)); // 开户银行
                    customer.setBankAccount(getCellString(row, 8)); // 银行帐号
                    customer.setTaxNo(getCellString(row, 9)); // 税号
                    customer.setRemark(getCellString(row, 11)); // 备注

                    // 处理停用字段：True→0(停用)，False→1(启用)
                    String statusStr = getCellString(row, 10);
                    if ("True".equalsIgnoreCase(statusStr) || "是".equalsIgnoreCase(statusStr) || "1".equals(statusStr)) {
                        customer.setStatus(0); // 停用
                    } else {
                        customer.setStatus(1); // 启用
                    }

                    save(customer);
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

    private boolean customerExists(String customerCode) {
        if (customerCode == null || customerCode.trim().isEmpty()) return false;
        return this.count(new LambdaQueryWrapper<Customer>().eq(Customer::getCustomerCode, customerCode.trim())) > 0;
    }
}
