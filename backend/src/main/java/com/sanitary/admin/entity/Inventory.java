package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("inventory")
public class Inventory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long materialId;
    private Long customerId;
    private Long processId;
    private String materialCode;
    private String materialName;
    private String customerName;
    private String spec;
    private String processName;
    private BigDecimal quantity;
    private LocalDateTime lastReceiveTime;
    private LocalDateTime lastShipTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
