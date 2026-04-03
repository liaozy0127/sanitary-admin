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
    @TableField(exist = false)
    private String spec;
    private Long processId;
    private String processName;
    private BigDecimal prevBalanceQty;
    private BigDecimal receiptQty;       // 本月收货（合计）
    private BigDecimal normalReceiptQty; // 本月收货（正常）
    private BigDecimal reworkQty;        // 本月收货（返工）
    private BigDecimal shipmentQty;      // 本月发货（合计）
    private BigDecimal goodsShipQty;     // 本月发货（良品）
    private BigDecimal defectiveQty;     // 原件退回数量
    private BigDecimal currBalanceQty;
    private BigDecimal unitPrice;
    private BigDecimal goodsAmount;          // 发货金额（良品）= goodsShipQty × unitPrice
    private BigDecimal reworkAmount;         // 发货金额（返工）= -reworkQty × unitPrice（负数）
    private BigDecimal prevFinancialBalance; // 上期结转金额（累计未抵扣负数，<=0 时结转，>0 清零）
    private String prevFinancialOrigin;      // 上期结转来源月份（最初产生负数的月份，如 "2025-12"）
    private BigDecimal shipmentAmount;       // 发货金额（合计）= goodsAmount + reworkAmount + prevFinancialBalance
    private String remark;
    @TableLogic
    private Integer deleted;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}