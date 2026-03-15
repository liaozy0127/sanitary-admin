package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.entity.Payment;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.mapper.PaymentMapper;
import com.sanitary.admin.service.PaymentService;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements PaymentService {

    private final GenerateNoUtil generateNoUtil;
    private final CustomerMapper customerMapper;

    @Override
    public Page<Payment> pageList(int page, int size, Long customerId, String startDate, String endDate) {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) {
            wrapper.eq(Payment::getCustomerId, customerId);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(Payment::getPaymentDate, LocalDate.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(Payment::getPaymentDate, LocalDate.parse(endDate));
        }
        wrapper.orderByDesc(Payment::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Payment createPayment(Payment payment) {
        payment.setPaymentNo(generateNoUtil.generate("SK", "payment", "payment_no"));
        if (!StringUtils.hasText(payment.getPaymentMethod())) {
            payment.setPaymentMethod("银行转账");
        }
        save(payment);
        return payment;
    }

    @Override
    public Map<String, Object> importExcel(MultipartFile file) {
        int success = 0;
        int fail = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();

        // 收款单列：0=收款单号, 1=事务类型, 2=付款方式, 3=日期, 4=客户代码, 5=客户名称,
        //           6=出货单号, 7=发货应收金额, 8=发货实收金额, 9=票据号, 10=收款人, 11=明细备注, 12=备注

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String paymentNo = getCellString(row, 0);
                    if (paymentNo.isEmpty()) {
                        skip++;
                        continue;
                    }

                    // 幂等：已存在则跳过
                    if (this.count(new LambdaQueryWrapper<Payment>().eq(Payment::getPaymentNo, paymentNo)) > 0) {
                        skip++;
                        continue;
                    }

                    String customerName = getCellString(row, 5);
                    Long customerId = findOrCreateCustomerIdByName(customerName);

                    Payment payment = new Payment();
                    payment.setPaymentNo(paymentNo);
                    payment.setPaymentDate(parseExcelDate(getCellString(row, 3)));
                    payment.setCustomerId(customerId);
                    payment.setCustomerName(customerName);
                    payment.setAmount(parseBigDecimal(getCellString(row, 8)));
                    payment.setPaymentMethod(getCellString(row, 2));
                    payment.setReferenceNo(getCellString(row, 9));
                    payment.setRemark(getCellString(row, 12));

                    getBaseMapper().insert(payment);
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
}
