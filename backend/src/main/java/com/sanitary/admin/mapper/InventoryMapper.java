package com.sanitary.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sanitary.admin.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    @Update("UPDATE inventory SET quantity = quantity + #{changeQty}, update_time = NOW() WHERE material_id = #{materialId} AND customer_id = #{customerId} AND process_id = #{processId}")
    int incrementQuantity(@Param("materialId") Long materialId, @Param("customerId") Long customerId, @Param("processId") Long processId, @Param("changeQty") BigDecimal changeQty);
}