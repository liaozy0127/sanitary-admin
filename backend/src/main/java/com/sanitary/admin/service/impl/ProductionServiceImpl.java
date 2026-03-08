package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Production;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Process;
import com.sanitary.admin.mapper.ProductionMapper;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ProcessMapper;
import com.sanitary.admin.service.ProductionService;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
public class ProductionServiceImpl extends ServiceImpl<ProductionMapper, Production> implements ProductionService {

    private final GenerateNoUtil generateNoUtil;
    private final CustomerMapper customerMapper;
    private final MaterialMapper materialMapper;
    private final ProcessMapper processMapper;

    @Override
    public Page<Production> pageList(int page, int size, String keyword, Long customerId, String prodStatus) {
        LambdaQueryWrapper<Production> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Production::getProductionNo, keyword)
                    .or().like(Production::getCustomerName, keyword)
                    .or().like(Production::getMaterialName, keyword));
        }
        if (customerId != null) {
            wrapper.eq(Production::getCustomerId, customerId);
        }
        if (StringUtils.hasText(prodStatus)) {
            wrapper.eq(Production::getProdStatus, prodStatus);
        }
        wrapper.orderByDesc(Production::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Production createProduction(Production production) {
        production.setProductionNo(generateNoUtil.generate("PC", "production", "production_no"));
        if (production.getProdStatus() == null) {
            production.setProdStatus("待生产");
        }
        if (production.getActualQty() == null) {
            production.setActualQty(BigDecimal.ZERO);
        }
        if (production.getPlannedQty() != null && production.getUnitPrice() != null) {
            production.setAmount(production.getPlannedQty().multiply(production.getUnitPrice()));
        }
        save(production);
        return production;
    }

    @Override
    public Map<String, Object> importFromExcel(MultipartFile file, String mode) {
        int success = 0;
        int fail = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();

        try (java.io.InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            String lastProductionNo = null;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String productionNo = getCellString(row, 1); // 排产单号 (col 1 in 0-based index)
                    if (productionNo.isEmpty()) {
                        if (lastProductionNo == null) { skip++; continue; }
                        productionNo = lastProductionNo;
                    } else {
                        lastProductionNo = productionNo;
                    }

                    // Check for duplicates (idempotency)
                    if (productionExists(productionNo)) {
                        skip++;
                        continue;
                    }

                    Production production = new Production();
                    production.setProductionNo(productionNo);
                    production.setProductionDate(parseExcelDate(getCellString(row, 2))); // 日期 (col 2)

                    String customerName = getCellString(row, 4); // 客户名称 (col 4)
                    if (!customerName.isEmpty()) {
                        Customer customer = customerMapper.selectOne(
                            new LambdaQueryWrapper<Customer>()
                                .eq(Customer::getCustomerName, customerName.trim())
                        );
                        if (customer != null) {
                            production.setCustomerId(customer.getId());
                            production.setCustomerName(customer.getCustomerName());
                        } else {
                            fail++;
                            errors.add("第" + (i + 1) + "行: 客户「" + customerName + "」不存在");
                            continue;
                        }
                    }

                    String materialName = getCellString(row, 5); // 产品名称 (col 5)
                    if (!materialName.isEmpty()) {
                        // 先按客户+名称查，找不到再按名称查
                        Long custId = production.getCustomerId();
                        LambdaQueryWrapper<Material> mWrapper = new LambdaQueryWrapper<Material>()
                                .eq(Material::getMaterialName, materialName.trim());
                        if (custId != null) mWrapper.eq(Material::getCustomerId, custId);
                        mWrapper.last("LIMIT 1");
                        Material material = materialMapper.selectOne(mWrapper);
                        if (material == null) {
                            material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                                    .eq(Material::getMaterialName, materialName.trim()).last("LIMIT 1"));
                        }
                        if (material != null) {
                            production.setMaterialId(material.getId());
                            production.setMaterialName(material.getMaterialName());
                            production.setMaterialCode(material.getMaterialCode());
                            production.setSpec(material.getSpec());
                        } else {
                            fail++;
                            errors.add("第" + (i + 1) + "行: 物料「" + materialName + "」不存在");
                            continue;
                        }
                    }

                    String processName = getCellString(row, 6); // 工艺名称 (col 6)
                    if (!processName.isEmpty()) {
                        Process process = processMapper.selectOne(
                            new LambdaQueryWrapper<Process>()
                                .eq(Process::getProcessName, processName.trim())
                        );
                        if (process != null) {
                            production.setProcessId(process.getId());
                            production.setProcessName(process.getProcessName());
                        } else {
                            fail++;
                            errors.add("第" + (i + 1) + "行: 工艺「" + processName + "」不存在");
                            continue;
                        }
                    }

                    production.setReceiptType(getCellString(row, 7)); // 收货类型 (col 7)
                    production.setUnit(getCellString(row, 8)); // 计量单位 (col 8)
                    production.setPlannedQty(parseBigDecimal(getCellString(row, 9))); // 排产数量 (col 9)

                    String outsourcePriceStr = getCellString(row, 12); // 委外单价 (col 12)
                    if (!outsourcePriceStr.isEmpty()) {
                        try {
                            production.setOutsourcePrice(new BigDecimal(outsourcePriceStr));
                        } catch (NumberFormatException e) {
                            production.setOutsourcePrice(BigDecimal.ZERO);
                        }
                    }

                    String platingPriceStr = getCellString(row, 14); // 电镀单价 (col 14)
                    if (!platingPriceStr.isEmpty()) {
                        try {
                            production.setPlatingPrice(new BigDecimal(platingPriceStr));
                        } catch (NumberFormatException e) {
                            production.setPlatingPrice(BigDecimal.ZERO);
                        }
                    }

                    production.setCustomerOrderNo(getCellString(row, 16)); // 客户单号 (col 16)
                    production.setProductionType(getCellString(row, 17)); // 排产方式 (col 17)

                    production.setProdStatus("待生产"); // 默认状态
                    production.setActualQty(BigDecimal.ZERO); // 实际数量初始化为0

                    if (production.getPlannedQty() != null && production.getUnitPrice() != null) {
                        production.setAmount(production.getPlannedQty().multiply(production.getUnitPrice()));
                    }

                    save(production);
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
                return null; // 日期解析失败，不中断
            }
        }
    }

    private BigDecimal parseBigDecimal(String s) {
        if (s == null || s.trim().isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private boolean productionExists(String productionNo) {
        if (productionNo == null || productionNo.trim().isEmpty()) return false;
        return this.count(new LambdaQueryWrapper<Production>().eq(Production::getProductionNo, productionNo.trim())) > 0;
    }
}
