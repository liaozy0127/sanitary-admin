package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Process;
import com.sanitary.admin.mapper.ProcessMapper;
import com.sanitary.admin.service.ProcessService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcessServiceImpl extends ServiceImpl<ProcessMapper, Process> implements ProcessService {

    @Override
    public Page<Process> pageList(int page, int size, String keyword) {
        LambdaQueryWrapper<Process> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Process::getProcessName, keyword)
                    .or().like(Process::getProcessCode, keyword));
        }
        wrapper.orderByAsc(Process::getPriorityNo).orderByDesc(Process::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public List<Map<String, Object>> listAll() {
        return list(new LambdaQueryWrapper<Process>()
                .eq(Process::getStatus, 1)
                .select(Process::getId, Process::getProcessName, Process::getProcessCode)
                .orderByAsc(Process::getPriorityNo))
                .stream()
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", p.getId());
                    m.put("name", p.getProcessName());
                    m.put("code", p.getProcessCode());
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
                    String processCode = getCellString(row, 0); // 工艺代码
                    if (processCode.isEmpty()) {
                        fail++;
                        errors.add("第" + (i + 1) + "行: 工艺代码不能为空");
                        continue;
                    }

                    // Check for duplicates (idempotency)
                    if (processExists(processCode)) {
                        skip++;
                        continue;
                    }

                    Process process = new Process();
                    process.setProcessCode(processCode);
                    process.setProcessName(getCellString(row, 1)); // 工艺名称
                    process.setProcessCategory(getCellString(row, 2)); // 工艺类别
                    process.setProcessNature(getCellString(row, 3)); // 工艺性质
                    process.setRemark(getCellString(row, 4)); // 备注

                    // 处理禁用字段：True→0(停用)，False→1(启用)
                    String statusStr = getCellString(row, 5);
                    if ("True".equalsIgnoreCase(statusStr) || "是".equalsIgnoreCase(statusStr) || "1".equals(statusStr)) {
                        process.setStatus(0); // 停用
                    } else {
                        process.setStatus(1); // 启用
                    }

                    process.setCreateTime(LocalDateTime.now());
                    process.setUpdateTime(LocalDateTime.now());

                    save(process);
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

    private boolean processExists(String processCode) {
        if (processCode == null || processCode.trim().isEmpty()) return false;
        return this.count(new LambdaQueryWrapper<Process>().eq(Process::getProcessCode, processCode.trim())) > 0;
    }
}
