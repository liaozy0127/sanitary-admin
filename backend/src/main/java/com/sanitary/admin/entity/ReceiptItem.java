package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("receipt_item")
public class ReceiptItem {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long receiptId;
    private String receiptNo;
    private Long materialId;
    private String materialName;
    private String materialCode;
    private String spec;
    private Long processId;
    private String processName;
    private String receiptSource;
    private BigDecimal quantity;
    private BigDecimal shippedQty;
    private BigDecimal unshippedQty;
    private BigDecimal plannedQty;
    private BigDecimal wareHousedQty;
    private BigDecimal unwareHousedQty;
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
