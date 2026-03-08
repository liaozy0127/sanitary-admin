package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("shipment_item")
public class ShipmentItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shipmentId;
    private String shipmentNo;
    private Long materialId;
    private String materialName;
    private String materialCode;
    private String spec;
    private Long processId;
    private String processName;
    private String shipmentType;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String customerOrderNo;
    private String detailRemark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
