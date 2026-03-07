package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("process")
public class Process {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String processCode;
    private String processName;
    private String processCategory;
    private String processNature;
    private String thicknessReq;
    private Integer defaultQuote;
    private Integer priorityNo;
    private String remark;
    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
