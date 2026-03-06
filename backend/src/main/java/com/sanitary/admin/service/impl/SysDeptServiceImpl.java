package com.sanitary.admin.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.SysDept;
import com.sanitary.admin.mapper.SysDeptMapper;
import com.sanitary.admin.service.SysDeptService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.List;

@Service
public class SysDeptServiceImpl extends ServiceImpl<SysDeptMapper, SysDept> implements SysDeptService {

    @Override
    public List<SysDept> listDepts(String deptName) {
        LambdaQueryWrapper<SysDept> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(deptName)) {
            wrapper.like(SysDept::getDeptName, deptName);
        }
        wrapper.orderByAsc(SysDept::getSort);
        return this.list(wrapper);
    }

    @Override
    public void removeDeptById(Long id) {
        // 校验是否存在子部门
        long childCount = this.count(
            new LambdaQueryWrapper<SysDept>().eq(SysDept::getParentId, id)
        );
        if (childCount > 0) {
            throw new RuntimeException("该部门下存在子部门，无法删除");
        }
        this.removeById(id);
    }
}
