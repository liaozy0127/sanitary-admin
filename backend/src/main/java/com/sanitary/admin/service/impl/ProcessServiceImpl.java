package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Process;
import com.sanitary.admin.mapper.ProcessMapper;
import com.sanitary.admin.service.ProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
}
