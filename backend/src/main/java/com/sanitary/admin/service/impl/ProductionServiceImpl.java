package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Production;
import com.sanitary.admin.entity.ProductionItem;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Process;
import com.sanitary.admin.mapper.ProductionMapper;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ProcessMapper;
import com.sanitary.admin.service.ProductionItemService;
import com.sanitary.admin.service.ProductionService;
import com.sanitary.admin.service.SysConfigService;
import com.sanitary.admin.util.ExcelExportUtil;
import com.sanitary.admin.util.GenerateNoUtil;
import com.sanitary.admin.util.PdfGenerator;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductionServiceImpl extends ServiceImpl<ProductionMapper, Production> implements ProductionService {

    private final GenerateNoUtil generateNoUtil;
    private final CustomerMapper customerMapper;
    private final MaterialMapper materialMapper;
    private final ProcessMapper processMapper;
    private final ProductionItemService productionItemService;
    private final SysConfigService sysConfigService;

    @Override
    public Page<Production> pageList(int page, int size, String keyword, Long customerId, String prodStatus) {
        LambdaQueryWrapper<Production> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Production::getProductionNo, keyword)
                    .or().like(Production::getCustomerName, keyword));
        }
        if (customerId != null) {
            wrapper.eq(Production::getCustomerId, customerId);
        }
        wrapper.orderByDesc(Production::getProductionDate).orderByDesc(Production::getId);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Production createProduction(Production production) {
        production.setProductionNo(generateNoUtil.generate("PC", "production", "production_no"));
        save(production);

        if (production.getItems() != null && !production.getItems().isEmpty()) {
            productionItemService.saveItems(production.getId(), production.getProductionNo(), production.getItems());
        }

        return production;
    }

    @Override
    @Transactional
    public Production updateProduction(Production production) {
        // 先删除旧的明细
        productionItemService.deleteByProductionId(production.getId());
        
        // 更新主表
        updateById(production);
        
        // 保存新的明细
        if (production.getItems() != null && !production.getItems().isEmpty()) {
            productionItemService.saveItems(production.getId(), production.getProductionNo(), production.getItems());
        }
        
        return production;
    }

    @Override
    @Transactional
    public boolean deleteProduction(Long id) {
        // 先删除明细
        productionItemService.deleteByProductionId(id);
        
        // 再删除主表记录
        return removeById(id);
    }

    @Override
    public Map<String, Object> importFromExcel(MultipartFile file, String mode) {
        int success = 0;
        int fail = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();

        Map<String, Production> productionMap = new LinkedHashMap<>();
        Map<String, List<ProductionItem>> itemsMap = new LinkedHashMap<>();

        String lastProductionNo = null;

        try (java.io.InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String productionNo = getCellString(row, 1);
                    if (productionNo.isEmpty()) {
                        if (lastProductionNo == null) { skip++; continue; }
                        productionNo = lastProductionNo;
                    } else {
                        lastProductionNo = productionNo;
                    }

                    // Skip if already exists in DB (idempotency)
                    if (productionExists(productionNo)) {
                        skip++;
                        continue;
                    }

                    // Build master record (only on first occurrence)
                    if (!productionMap.containsKey(productionNo)) {
                        Production production = new Production();
                        production.setProductionNo(productionNo);
                        production.setProductionDate(parseExcelDate(getCellString(row, 2)));

                        String customerName = getCellString(row, 4);
                        if (!customerName.isEmpty()) {
                            Customer customer = customerMapper.selectOne(
                                new LambdaQueryWrapper<Customer>()
                                    .eq(Customer::getCustomerName, customerName.trim())
                                    .last("LIMIT 1")
                            );
                            if (customer != null) {
                                production.setCustomerId(customer.getId());
                                production.setCustomerName(customer.getCustomerName());
                            } else {
                                fail++;
                                errors.add("第" + (i + 1) + "行: 客户「" + customerName + "」不存在");
                                continue;
                            }
                        } else {
                            fail++;
                            errors.add("第" + (i + 1) + "行: 客户名称为空");
                            continue;
                        }

                        productionMap.put(productionNo, production);
                        itemsMap.put(productionNo, new ArrayList<>());
                    }

                    // Build item record for this row
                    ProductionItem item = new ProductionItem();
                    item.setProductionNo(productionNo);

                    String materialName = getCellString(row, 5);
                    if (!materialName.isEmpty()) {
                        Long custId = productionMap.get(productionNo).getCustomerId();
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
                            item.setMaterialId(material.getId());
                            item.setMaterialName(material.getMaterialName());
                            item.setMaterialCode(material.getMaterialCode());
                            item.setSpec(material.getSpec());
                        } else {
                            item.setMaterialName(materialName.trim());
                        }
                    }

                    String processName = getCellString(row, 6);
                    if (!processName.isEmpty()) {
                        Process process = processMapper.selectOne(
                            new LambdaQueryWrapper<Process>()
                                .eq(Process::getProcessName, processName.trim())
                                .last("LIMIT 1")
                        );
                        if (process != null) {
                            item.setProcessId(process.getId());
                            item.setProcessName(process.getProcessName());
                        } else {
                            item.setProcessName(processName.trim());
                        }
                    }

                    item.setReceiptType(getCellString(row, 7));
                    item.setUnit(getCellString(row, 8));
                    item.setPlannedQty(parseQty(getCellString(row, 9)));
                    item.setPlatingPrice(parsePrice(getCellString(row, 11)));
                    item.setProductionType(getCellString(row, 12));
                    item.setDetailRemark(getCellString(row, 13));

                    itemsMap.get(productionNo).add(item);

                } catch (Exception e) {
                    fail++;
                    errors.add("第" + (i + 1) + "行: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel解析失败: " + e.getMessage());
        }

        // Batch save all productions and their items
        for (Map.Entry<String, Production> entry : productionMap.entrySet()) {
            String productionNo = entry.getKey();
            Production production = entry.getValue();
            try {
                getBaseMapper().insert(production);
                List<ProductionItem> items = itemsMap.get(productionNo);
                productionItemService.saveItems(production.getId(), production.getProductionNo(), items);
                success++;
            } catch (Exception e) {
                fail++;
                errors.add("单号" + productionNo + ": " + e.getMessage());
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

    private boolean productionExists(String productionNo) {
        if (productionNo == null || productionNo.trim().isEmpty()) return false;
        return this.count(new LambdaQueryWrapper<Production>().eq(Production::getProductionNo, productionNo.trim())) > 0;
    }

    @Override
    public void exportExcel(HttpServletResponse response, String keyword, Long customerId, String startDate, String endDate) {
        LambdaQueryWrapper<Production> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Production::getProductionNo, keyword).or().like(Production::getCustomerName, keyword));
        }
        if (customerId != null) wrapper.eq(Production::getCustomerId, customerId);
        if (StringUtils.hasText(startDate)) wrapper.ge(Production::getProductionDate, startDate);
        if (StringUtils.hasText(endDate)) wrapper.le(Production::getProductionDate, endDate);
        wrapper.orderByDesc(Production::getProductionDate).orderByDesc(Production::getId).last("LIMIT 5000");
        List<Production> productions = this.list(wrapper);

        List<Long> ids = productions.stream().map(Production::getId).collect(java.util.stream.Collectors.toList());
        List<ProductionItem> allItems = ids.isEmpty() ? new ArrayList<>() :
            productionItemService.listByProductionIds(ids);
        java.util.Map<Long, List<ProductionItem>> itemMap = allItems.stream()
            .collect(java.util.stream.Collectors.groupingBy(ProductionItem::getProductionId));

        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("排产单");
        String[] headers = {"排产单号","排产日期","客户名称","备注","物料编码","物料名称","型号规格","工艺名称",
            "收货类型","单位","排产数量","单价","排产方式","明细备注"};
        ExcelExportUtil.writeTitleRow(sheet, wb, "排产单", headers.length);
        ExcelExportUtil.writeHeaderRow(sheet, wb, headers);

        CellStyle masterS = ExcelExportUtil.masterRowStyle(wb);
        CellStyle masterDateS = ExcelExportUtil.masterRowDateStyle(wb);
        CellStyle s0 = ExcelExportUtil.dataStyle(wb, false);
        CellStyle s1 = ExcelExportUtil.dataStyle(wb, true);
        CellStyle n0 = ExcelExportUtil.numStyle(wb, false);
        CellStyle n1 = ExcelExportUtil.numStyle(wb, true);

        BigDecimal totalPlanned = BigDecimal.ZERO;

        int rowIdx = 2;
        int detailCount = 0;
        for (Production p : productions) {
            List<ProductionItem> items = itemMap.getOrDefault(p.getId(), new ArrayList<>());
            Row masterRow = sheet.createRow(rowIdx++);
            ExcelExportUtil.setCell(masterRow, 0, p.getProductionNo(), masterS);
            ExcelExportUtil.setCell(masterRow, 1, ExcelExportUtil.fmtDate(p.getProductionDate()), masterDateS);
            ExcelExportUtil.setCell(masterRow, 2, p.getCustomerName(), masterS);
            ExcelExportUtil.setCell(masterRow, 3, p.getRemark(), masterS);
            for (int i = 4; i < headers.length; i++) ExcelExportUtil.setCell(masterRow, i, "", masterS);

            for (ProductionItem item : items) {
                if (detailCount >= 50000) break;
                boolean even = (detailCount % 2 == 0);
                CellStyle s = even ? s0 : s1;
                CellStyle ns = even ? n0 : n1;
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < 4; i++) ExcelExportUtil.setCell(row, i, "", s);
                ExcelExportUtil.setCell(row, 4, item.getMaterialCode(), s);
                ExcelExportUtil.setCell(row, 5, item.getMaterialName(), s);
                ExcelExportUtil.setCell(row, 6, item.getSpec(), s);
                ExcelExportUtil.setCell(row, 7, item.getProcessName(), s);
                ExcelExportUtil.setCell(row, 8, item.getReceiptType(), s);
                ExcelExportUtil.setCell(row, 9, item.getUnit(), s);
                ExcelExportUtil.setCell(row, 10, item.getPlannedQty(), ns);
                ExcelExportUtil.setCell(row, 11, item.getPlatingPrice(), ns);
                ExcelExportUtil.setCell(row, 12, item.getProductionType(), s);
                ExcelExportUtil.setCell(row, 13, item.getDetailRemark(), s);
                if (item.getPlannedQty() != null) totalPlanned = totalPlanned.add(item.getPlannedQty());
                detailCount++;
            }
        }

        CellStyle sumS = ExcelExportUtil.summaryStyle(wb);
        CellStyle sumN = ExcelExportUtil.summaryNumStyle(wb);
        Row sumRow = sheet.createRow(rowIdx);
        ExcelExportUtil.setCell(sumRow, 0, "合计", sumS);
        for (int i = 1; i <= 9; i++) ExcelExportUtil.setCell(sumRow, i, "", sumS);
        ExcelExportUtil.setCell(sumRow, 10, totalPlanned, sumN);
        for (int i = 11; i < headers.length; i++) ExcelExportUtil.setCell(sumRow, i, "", sumS);

        sheet.createFreezePane(0, 2);
        ExcelExportUtil.autoSize(sheet, headers.length);
        try {
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            ExcelExportUtil.writeResponse(wb, response, "排产单_" + today + ".xlsx");
        } catch (IOException e) {
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }

    @Override
    public void exportPdf(Long id, HttpServletResponse response) {
        Production production = getById(id);
        if (production == null) throw new RuntimeException("排产单不存在");
        production.setItems(productionItemService.listByProductionId(id));

        Map<String, String> config = sysConfigService.getPrintConfig();
        String docTitle = nvl(config.get("printTitleProduction"), "致恒（致越）金属表面加工厂生产安排表");
        String sig1 = nvl(config.get("printSignature1Label"), "生产班长");
        String sig2 = nvl(config.get("printSignature2Label"), "仓管");
        String sig3 = nvl(config.get("printSignature3Label"), "签名");

        List<ProductionItem> items = production.getItems() == null ? new ArrayList<>() : production.getItems();
        BigDecimal totalQty = items.stream()
            .map(it -> it.getPlannedQty() == null ? BigDecimal.ZERO : it.getPlannedQty())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder rows = new StringBuilder();
        for (ProductionItem item : items) {
            rows.append("<tr>")
                .append("<td>").append(esc(production.getCustomerName())).append("</td>")
                .append("<td>").append(esc(item.getMaterialName())).append("</td>")
                .append("<td>").append(esc(item.getSpec())).append("</td>")
                .append("<td>").append(esc(item.getProcessName())).append("</td>")
                .append("<td style=\"text-align:right\">").append(item.getPlannedQty() != null ? item.getPlannedQty().toPlainString() : "").append("</td>")
                .append("<td style=\"text-align:center\">").append(esc(item.getProductionType())).append("</td>")
                .append("<td></td><td></td><td></td><td></td>")
                .append("<td>").append(esc(item.getDetailRemark())).append("</td>")
                .append("</tr>");
        }

        String prodDate = production.getProductionDate() != null ? production.getProductionDate().toString() : "";
        String remark = nvl(production.getRemark(), "");

        String html = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n"
            + "<head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>\n"
            + "<style type=\"text/css\">\n"
            + "@page {\n"
            + "  size: 241mm 120mm;\n"
            + "  margin: 5mm 5mm 18mm 5mm;\n"
            + "  @bottom-center { content: element(pageFooter); }\n"
            + "}\n"
            + "body { font-family: 'WenQuanYi Zen Hei', 'AR PL UMing CN', serif; font-size: 9pt; margin:0; padding:0; }\n"
            + "#pageFooter {\n"
            + "  position: running(pageFooter);\n"
            + "  width: 100%;\n"
            + "  font-size:9pt;\n"
            + "}\n"
            + "#pageFooter .remark { margin-bottom:2pt; }\n"
            + "table.sigs { width:100%; border-collapse:collapse; }\n"
            + "table.sigs td { width:33%; border:none; padding:0; }\n"
            + "table.main { width:100%; border-collapse:collapse; font-size:8pt; }\n"
            + "table.main th, table.main td { border:1pt solid #0066CC; padding:2pt 3pt; }\n"
            + "table.main th { background-color:#D9E8F7; text-align:center; font-weight:bold; }\n"
            + "table.main td.hd-title { border:none; text-align:center; font-size:12pt; font-weight:bold; padding:2pt 0 3pt 0; background:white; }\n"
            + "table.main td.hd-meta { border:none; font-size:9pt; padding:1pt 0 3pt 0; background:white; }\n"
            + "</style>\n"
            + "</head><body>\n"
            + "<div id=\"pageFooter\">\n"
            + "  <div class=\"remark\">备注：" + esc(remark) + "</div>\n"
            + "  <table class=\"sigs\"><tr>\n"
            + "    <td>" + esc(sig3) + "：________________</td>\n"
            + "    <td>" + esc(sig1) + "：________________</td>\n"
            + "    <td>" + esc(sig2) + "：________________</td>\n"
            + "  </tr></table>\n"
            + "</div>\n"
            + "<table class=\"main\">\n"
            + "  <thead>\n"
            + "    <tr><td colspan=\"11\" class=\"hd-title\">" + esc(docTitle) + "</td></tr>\n"
            + "    <tr><td colspan=\"11\" class=\"hd-meta\">排产日期：" + esc(prodDate)
            + "&#160;&#160;&#160;&#160;单号：" + esc(production.getProductionNo())
            + "&#160;&#160;&#160;&#160;班次：&#160;&#160;&#160;&#160;客户：" + esc(production.getCustomerName()) + "</td></tr>\n"
            + "    <tr>\n"
            + "      <th rowspan=\"2\">客户</th><th rowspan=\"2\">品名</th><th rowspan=\"2\">规格</th>"
            + "<th rowspan=\"2\">工艺</th><th rowspan=\"2\">数量</th><th rowspan=\"2\">类型</th>"
            + "<th colspan=\"4\">完成情况</th><th rowspan=\"2\">备注</th>\n"
            + "    </tr>\n"
            + "    <tr>\n"
            + "      <th>1良品</th><th>2良品</th><th>3良品</th><th>不良</th>\n"
            + "    </tr>\n"
            + "  </thead>\n"
            + "  <tbody>\n"
            + rows.toString()
            + "    <tr>\n"
            + "      <td colspan=\"4\" style=\"text-align:right;font-weight:bold;\">合计</td>\n"
            + "      <td style=\"text-align:right;font-weight:bold;\">" + totalQty.toPlainString() + "</td>\n"
            + "      <td colspan=\"6\"></td>\n"
            + "    </tr>\n"
            + "  </tbody>\n"
            + "</table>\n"
            + "</body></html>";

        try {
            String filename = URLEncoder.encode("排产单_" + production.getProductionNo() + ".pdf", StandardCharsets.UTF_8);
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename*=UTF-8''" + filename);
            PdfGenerator.render(html, response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException("PDF生成失败: " + e.getMessage(), e);
        }
    }

    private static String nvl(String v, String def) {
        return (v != null && !v.isEmpty()) ? v : def;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
