package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_log")
public class SysLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String operation;
    private String method;
    private String params;
    private String ip;
    private Long time;
    private Integer status;
    private String errorMsg;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
