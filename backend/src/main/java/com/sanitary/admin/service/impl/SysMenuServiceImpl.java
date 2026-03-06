package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.SysMenu;
import com.sanitary.admin.mapper.SysMenuMapper;
import com.sanitary.admin.service.SysMenuService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    @Override
    public List<SysMenu> listMenus(String menuName) {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(menuName)) wrapper.like(SysMenu::getMenuName, menuName);
        wrapper.orderByAsc(SysMenu::getSort);
        return this.list(wrapper);
    }

    @Override
    public List<SysMenu> getMenuTree() {
        List<SysMenu> all = this.list(new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));
        Set<Long> visited = new HashSet<>();
        return all.stream()
            .filter(m -> m.getParentId() == null || m.getParentId() == 0)
            .map(m -> {
                m.setChildren(getChildren(m.getId(), all, visited));
                return m;
            })
            .collect(Collectors.toList());
    }

    /**
     * 递归获取子菜单，带循环引用保护。
     *
     * @param parentId 父节点 ID
     * @param all      所有菜单（全量内存）
     * @param visited  已访问节点集合，防止环形引用导致 StackOverflow
     */
    private List<SysMenu> getChildren(Long parentId, List<SysMenu> all, Set<Long> visited) {
        if (parentId == null || !visited.add(parentId)) {
            // 已访问过该节点，环形引用，直接返回空列表
            return Collections.emptyList();
        }
        return all.stream()
            .filter(m -> parentId.equals(m.getParentId()))
            .map(m -> {
                m.setChildren(getChildren(m.getId(), all, new HashSet<>(visited)));
                return m;
            })
            .collect(Collectors.toList());
    }
}
