package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("inventory_log")
public class InventoryLog {

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

    /** 1=收货 2=发货 3=返工 */
    private Integer changeType;

    private BigDecimal changeQty;
    private BigDecimal beforeQty;
    private BigDecimal afterQty;

    /** RECEIPT / SHIPMENT / REWORK */
    private String orderType;

    private Long orderId;
    private String orderNo;
    private LocalDate orderDate;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
