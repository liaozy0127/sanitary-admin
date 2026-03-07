package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("statement")
public class Statement {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String statementNo;

    @NotBlank(message = "对账月份不能为空")
    private String statementMonth;

    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    private BigDecimal receiptQty;
    private BigDecimal shipmentQty;
    private BigDecimal receiptAmount;
    private BigDecimal shipmentAmount;
    private String remark;
    private String status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
