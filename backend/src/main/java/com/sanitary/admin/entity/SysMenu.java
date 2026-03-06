package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.LocalDateTime;
import java.util.List;

@Data
@ToString(exclude = "children")
@EqualsAndHashCode(exclude = "children")
@TableName("sys_menu")
public class SysMenu {
    @TableId(type = IdType.AUTO)
    private Long id;
    @NotBlank(message = "菜单名称不能为空")
    private String menuName;
    private String menuPath;
    private String menuIcon;
    private Long parentId;
    private Integer sort;
    private Integer menuType; // 1=目录 2=菜单 3=按钮
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
    @TableField(exist = false)
    private List<SysMenu> children;
}
