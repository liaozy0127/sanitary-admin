package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("production_item")
public class ProductionItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productionId;
    private String productionNo;
    private Long materialId;
    private String materialName;
    private String materialCode;
    private String spec;
    private Long processId;
    private String processName;
    private String receiptType;
    private String unit;
    private BigDecimal plannedQty;
    private BigDecimal actualQty;
    @TableField("unwarehoused_qty")
    private BigDecimal unwareHousedQty;
    private BigDecimal outsourcePrice;
    private BigDecimal platingPrice;
    private BigDecimal platingAmount;
    private String customerOrderNo;
    private String productionType;
    private String detailRemark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
