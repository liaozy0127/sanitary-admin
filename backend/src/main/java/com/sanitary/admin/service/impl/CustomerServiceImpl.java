package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.service.CustomerService;
import com.sanitary.admin.util.ExcelExportUtil;
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
                    customer.setCustomerName(getCellString(row, 1));  // 客户名称
                    customer.setCustomerType(getCellString(row, 3));  // 客户类型（col3）
                    customer.setAddress(getCellString(row, 5));       // 地址（col5）
                    customer.setBankName(getCellString(row, 7));      // 开户银行（col7）
                    customer.setTaxNo(getCellString(row, 8));         // 税号（col8）
                    customer.setBankAccount(getCellString(row, 9));   // 银行帐号（col9）
                    customer.setSalesperson(getCellString(row, 10));  // 业务员（col10）
                    customer.setContactPerson(getCellString(row, 12)); // 联系人（col12）
                    customer.setContactPhone(getCellString(row, 13)); // 联系电话（col13）
                    customer.setRemark(getCellString(row, 19));       // 备注（col19）

                    // 处理停用字段：True→0(停用)，False→1(启用)（col15）
                    String statusStr = getCellString(row, 15);
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

    @Override
    public void exportExcel(HttpServletResponse response, String keyword, String customerType) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Customer::getCustomerName, keyword).or().like(Customer::getCustomerCode, keyword));
        }
        if (StringUtils.hasText(customerType)) {
            wrapper.eq(Customer::getCustomerType, customerType);
        }
        wrapper.orderByDesc(Customer::getCreateTime).last("LIMIT 50000");
        List<Customer> list = this.list(wrapper);

        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("客户档案");

        String[] headers = {"客户编码","客户名称","客户类型","联系人","联系电话","地址","业务员","开户银行","银行账号","税号","状态","创建时间","备注"};
        ExcelExportUtil.writeTitleRow(sheet, wb, "客户档案", headers.length);
        ExcelExportUtil.writeHeaderRow(sheet, wb, headers);

        CellStyle s0 = ExcelExportUtil.dataStyle(wb, false);
        CellStyle s1 = ExcelExportUtil.dataStyle(wb, true);
        CellStyle d0 = ExcelExportUtil.dateStyle(wb, false);
        CellStyle d1 = ExcelExportUtil.dateStyle(wb, true);

        int rowIdx = 2;
        for (Customer c : list) {
            boolean even = (rowIdx % 2 == 0);
            CellStyle s = even ? s1 : s0;
            CellStyle ds = even ? d1 : d0;
            Row row = sheet.createRow(rowIdx++);
            ExcelExportUtil.setCell(row, 0, c.getCustomerCode(), s);
            ExcelExportUtil.setCell(row, 1, c.getCustomerName(), s);
            ExcelExportUtil.setCell(row, 2, c.getCustomerType(), s);
            ExcelExportUtil.setCell(row, 3, c.getContactPerson(), s);
            ExcelExportUtil.setCell(row, 4, c.getContactPhone(), s);
            ExcelExportUtil.setCell(row, 5, c.getAddress(), s);
            ExcelExportUtil.setCell(row, 6, c.getSalesperson(), s);
            ExcelExportUtil.setCell(row, 7, c.getBankName(), s);
            ExcelExportUtil.setCell(row, 8, c.getBankAccount(), s);
            ExcelExportUtil.setCell(row, 9, c.getTaxNo(), s);
            ExcelExportUtil.setCell(row, 10, c.getStatus() != null && c.getStatus() == 1 ? "启用" : "禁用", s);
            ExcelExportUtil.setCell(row, 11, ExcelExportUtil.fmtDateTime(c.getCreateTime()), ds);
            ExcelExportUtil.setCell(row, 12, c.getRemark(), s);
        }

        sheet.createFreezePane(0, 2);
        ExcelExportUtil.autoSize(sheet, headers.length);

        try {
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            ExcelExportUtil.writeResponse(wb, response, "客户档案_" + today + ".xlsx");
        } catch (IOException e) {
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }
}
