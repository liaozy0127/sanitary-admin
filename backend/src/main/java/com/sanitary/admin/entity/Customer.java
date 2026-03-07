package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("customer")
public class Customer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String customerCode;
    private String customerName;
    private String areaName;
    private String customerType;
    private String industry;
    private String address;
    private String contactPerson;
    private String contactPhone;
    private String email;
    private String salesperson;
    private String bankName;
    private String bankAccount;
    private String taxNo;
    private String financeContact;
    private String financePhone;
    private BigDecimal priceAdjustRate;
    private Integer shipWarningDays;
    private String remark;
    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
