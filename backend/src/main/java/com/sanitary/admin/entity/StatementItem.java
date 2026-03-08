package com.sanitary.admin.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("statement_item")
public class StatementItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long statementId;
    private String statementNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private Long processId;
    private String processName;
    private BigDecimal prevBalanceQty;
    private BigDecimal receiptQty;
    private BigDecimal shipmentQty;
    private BigDecimal currBalanceQty;
    private BigDecimal unitPrice;
    private BigDecimal shipmentAmount;
    private String remark;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}