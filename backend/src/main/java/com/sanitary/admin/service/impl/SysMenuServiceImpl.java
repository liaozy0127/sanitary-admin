package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.SysMenu;
import com.sanitary.admin.mapper.SysMenuMapper;
import com.sanitary.admin.service.SysMenuService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.List;
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
        return all.stream()
            .filter(m -> m.getParentId() == null || m.getParentId() == 0)
            .peek(m -> m.setChildren(getChildren(m.getId(), all)))
            .collect(Collectors.toList());
    }

    private List<SysMenu> getChildren(Long parentId, List<SysMenu> all) {
        return all.stream()
            .filter(m -> parentId.equals(m.getParentId()))
            .peek(m -> m.setChildren(getChildren(m.getId(), all)))
            .collect(Collectors.toList());
    }
}
