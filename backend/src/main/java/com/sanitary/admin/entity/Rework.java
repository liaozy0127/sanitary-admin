package com.sanitary.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("rework")
public class Rework {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String reworkNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @NotNull(message = "返工日期不能为空")
    private LocalDate reworkDate;

    @NotNull(message = "客户不能为空")
    private Long customerId;

    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    private Long materialId;

    @NotBlank(message = "物料名称不能为空")
    private String materialName;

    private String materialCode;
    private String spec;
    private Long processId;
    private String processName;

    @NotNull(message = "返工数量不能为空")
    private BigDecimal quantity;

    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String reworkReason;
    private String reworkStatus;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
