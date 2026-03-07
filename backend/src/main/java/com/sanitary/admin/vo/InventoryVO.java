package com.sanitary.admin.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存视图对象（实时计算）
 */
@Data
public class InventoryVO {
    private Long customerId;
    private String customerName;
    private String materialCode;
    private String materialName;
    private String spec;
    private BigDecimal receiptQty;
    private BigDecimal shipmentQty;
    private BigDecimal currentStock;
    private BigDecimal lastPrice;
}
