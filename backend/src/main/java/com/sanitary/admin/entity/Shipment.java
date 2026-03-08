package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("shipment")
public class Shipment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String shipmentNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "发货日期不能为空")
    private LocalDate shipmentDate;

    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    private String remark;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private List<ShipmentItem> items;
}
