package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName(value = "rework_item")
public class ReworkItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("rework_id")
    private Long reworkId;

    @TableField("rework_no")
    private String reworkNo;

    @TableField("material_id")
    private Long materialId;

    @TableField("material_name")
    private String materialName;

    @TableField("material_code")
    private String materialCode;

    @TableField("spec")
    private String spec;

    @TableField("process_id")
    private Long processId;

    @TableField("process_name")
    private String processName;

    @TableField("quantity")
    private BigDecimal quantity;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    @TableField("amount")
    private BigDecimal amount;

    @TableField("rework_reason")
    private String reworkReason;

    @TableField("detail_remark")
    private String detailRemark;

    @TableField("deleted")
    private Integer deleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}