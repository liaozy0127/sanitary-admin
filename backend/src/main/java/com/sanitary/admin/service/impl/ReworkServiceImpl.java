package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Rework;
import com.sanitary.admin.entity.ReworkItem;
import com.sanitary.admin.mapper.ReworkMapper;
import com.sanitary.admin.service.InventoryService;
import com.sanitary.admin.service.ReworkItemService;
import com.sanitary.admin.service.ReworkService;
import com.sanitary.admin.util.ExcelExportUtil;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReworkServiceImpl extends ServiceImpl<ReworkMapper, Rework> implements ReworkService {

    private final GenerateNoUtil generateNoUtil;
    private final InventoryService inventoryService;
    private final ReworkItemService reworkItemService;

    @Override
    public Page<Rework> pageList(int page, int size, String keyword, Long customerId, String reworkStatus) {
        LambdaQueryWrapper<Rework> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Rework::getReworkNo, keyword)
                    .or().like(Rework::getCustomerName, keyword));
        }
        if (customerId != null) {
            wrapper.eq(Rework::getCustomerId, customerId);
        }
        if (StringUtils.hasText(reworkStatus)) {
            wrapper.eq(Rework::getReworkStatus, reworkStatus);
        }
        wrapper.orderByDesc(Rework::getReworkDate).orderByDesc(Rework::getId);
        return page(new Page<>(page, size), wrapper);
    }

    public Rework getByIdWithItems(Long id) {
        Rework rework = super.getById(id);
        if (rework != null) {
            rework.setItems(reworkItemService.getByReworkId(id));
        }
        return rework;
    }

    @Override
    @Transactional
    public Rework createRework(Rework rework) {
        rework.setReworkNo(generateNoUtil.generate("FG", "rework", "rework_no"));
        if (rework.getReworkStatus() == null) {
            rework.setReworkStatus("待返工");
        }
        save(rework);

        // 保存明细项
        if (rework.getItems() != null && !rework.getItems().isEmpty()) {
            reworkItemService.saveItems(rework.getId(), rework.getReworkNo(), rework.getItems());
        }

        return rework;
    }

    @Override
    @Transactional
    public Rework updateRework(Rework rework) {
        // 若请求未传 reworkNo，从数据库补充（避免明细插入时 NOT NULL 约束报错）
        if (rework.getReworkNo() == null) {
            Rework existing = getById(rework.getId());
            if (existing != null) {
                rework.setReworkNo(existing.getReworkNo());
            }
        }

        // 先删除原有的明细项
        reworkItemService.deleteByReworkId(rework.getId());

        updateById(rework);

        // 重新保存明细项
        if (rework.getItems() != null && !rework.getItems().isEmpty()) {
            reworkItemService.saveItems(rework.getId(), rework.getReworkNo(), rework.getItems());
        }

        return rework;
    }

    @Override
    @Transactional
    public boolean deleteRework(Long id) {
        // 先删除明细项
        reworkItemService.deleteByReworkId(id);
        // 再删除主单
        return removeById(id);
    }

    @Override
    @Transactional
    public void confirm(Long id) {
        Rework rework = getById(id);
        if (rework == null) {
            throw new RuntimeException("返工单不存在");
        }
        rework.setReworkStatus("已完成");
        updateById(rework);
    }

    @Override
    public void exportExcel(HttpServletResponse response, String keyword, Long customerId, String startDate, String endDate) {
        LambdaQueryWrapper<Rework> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Rework::getReworkNo, keyword).or().like(Rework::getCustomerName, keyword));
        }
        if (customerId != null) wrapper.eq(Rework::getCustomerId, customerId);
        if (StringUtils.hasText(startDate)) wrapper.ge(Rework::getReworkDate, startDate);
        if (StringUtils.hasText(endDate)) wrapper.le(Rework::getReworkDate, endDate);
        wrapper.orderByDesc(Rework::getReworkDate).last("LIMIT 5000");
        List<Rework> reworks = this.list(wrapper);

        List<Long> ids = reworks.stream().map(Rework::getId).collect(java.util.stream.Collectors.toList());
        List<ReworkItem> allItems = ids.isEmpty() ? new ArrayList<>() :
            reworkItemService.listByReworkIds(ids);
        java.util.Map<Long, List<ReworkItem>> itemMap = allItems.stream()
            .collect(java.util.stream.Collectors.groupingBy(ReworkItem::getReworkId));

        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("返工单");
        String[] headers = {"返工单号","返工日期","客户名称","状态","备注","物料编码","物料名称","型号规格","工艺名称",
            "数量","单价","金额","返工原因","明细备注"};
        ExcelExportUtil.writeTitleRow(sheet, wb, "返工单", headers.length);
        ExcelExportUtil.writeHeaderRow(sheet, wb, headers);

        CellStyle masterS = ExcelExportUtil.masterRowStyle(wb);
        CellStyle masterDateS = ExcelExportUtil.masterRowDateStyle(wb);
        CellStyle s0 = ExcelExportUtil.dataStyle(wb, false);
        CellStyle s1 = ExcelExportUtil.dataStyle(wb, true);
        CellStyle n0 = ExcelExportUtil.numStyle(wb, false);
        CellStyle n1 = ExcelExportUtil.numStyle(wb, true);

        BigDecimal totalQty = BigDecimal.ZERO, totalAmount = BigDecimal.ZERO;

        int rowIdx = 2;
        int detailCount = 0;
        for (Rework r : reworks) {
            List<ReworkItem> items = itemMap.getOrDefault(r.getId(), new ArrayList<>());
            Row masterRow = sheet.createRow(rowIdx++);
            ExcelExportUtil.setCell(masterRow, 0, r.getReworkNo(), masterS);
            ExcelExportUtil.setCell(masterRow, 1, ExcelExportUtil.fmtDate(r.getReworkDate()), masterDateS);
            ExcelExportUtil.setCell(masterRow, 2, r.getCustomerName(), masterS);
            ExcelExportUtil.setCell(masterRow, 3, r.getReworkStatus(), masterS);
            ExcelExportUtil.setCell(masterRow, 4, r.getRemark(), masterS);
            for (int i = 5; i < headers.length; i++) ExcelExportUtil.setCell(masterRow, i, "", masterS);

            for (ReworkItem item : items) {
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
                ExcelExportUtil.setCell(row, 10, item.getUnitPrice(), ns);
                ExcelExportUtil.setCell(row, 11, item.getAmount(), ns);
                ExcelExportUtil.setCell(row, 12, item.getReworkReason(), cs);
                ExcelExportUtil.setCell(row, 13, item.getDetailRemark(), cs);
                if (item.getQuantity() != null) totalQty = totalQty.add(item.getQuantity());
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
        ExcelExportUtil.setCell(sumRow, 10, "", sumS);
        ExcelExportUtil.setCell(sumRow, 11, totalAmount, sumN);
        ExcelExportUtil.setCell(sumRow, 12, "", sumS);
        ExcelExportUtil.setCell(sumRow, 13, "", sumS);

        sheet.createFreezePane(0, 2);
        ExcelExportUtil.autoSize(sheet, headers.length);
        try {
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            ExcelExportUtil.writeResponse(wb, response, "返工单_" + today + ".xlsx");
        } catch (IOException e) {
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }
}
