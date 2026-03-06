package com.sanitary.admin.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.SysDept;
import java.util.List;

public interface SysDeptService extends IService<SysDept> {
    List<SysDept> listDepts(String deptName);

    /**
     * 删除部门（删除前校验是否有子部门）
     */
    void removeDeptById(Long id);
}
