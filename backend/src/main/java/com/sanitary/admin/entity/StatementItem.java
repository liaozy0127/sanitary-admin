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
    private BigDecimal reworkQty;     // 本月返工收货数量（免费，已从计费中扣除）
    private BigDecimal shipmentQty;
    private BigDecimal defectiveQty;  // 原件退回数量
    private BigDecimal currBalanceQty;
    private BigDecimal unitPrice;
    private BigDecimal goodsAmount;   // 良品金额
    private BigDecimal shipmentAmount;
    private String remark;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}