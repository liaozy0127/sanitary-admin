package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("material_process_price")
public class MaterialProcessPrice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long customerId;
    private String customerName;
    private Long materialId;
    private String materialName;
    private String materialCode;
    private String spec;
    private Long processId;
    private String processName;
    private BigDecimal unitPrice;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
