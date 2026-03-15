package com.sanitary.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sanitary.admin.entity.Inventory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    @Update("UPDATE inventory SET quantity = quantity + #{changeQty}, update_time = NOW() WHERE material_id = #{materialId} AND customer_id = #{customerId} AND process_id = #{processId}")
    int incrementQuantity(@Param("materialId") Long materialId, @Param("customerId") Long customerId, @Param("processId") Long processId, @Param("changeQty") BigDecimal changeQty);

    /**
     * 聚合收货明细：按(material_id, customer_id, process_id)汇总收货数量及维度信息
     */
    @Select("SELECT " +
            "  ri.material_id, " +
            "  r.customer_id, " +
            "  COALESCE(ri.process_id, 0) AS process_id, " +
            "  ri.material_code, " +
            "  ri.material_name, " +
            "  r.customer_name, " +
            "  ri.spec, " +
            "  ri.process_name, " +
            "  SUM(ri.quantity) AS receipt_qty, " +
            "  MAX(r.receipt_date) AS last_receive_date " +
            "FROM receipt_item ri " +
            "JOIN receipt r ON r.id = ri.receipt_id " +
            "WHERE ri.deleted = 0 AND r.deleted = 0 AND ri.material_id IS NOT NULL " +
            "GROUP BY ri.material_id, r.customer_id, COALESCE(ri.process_id, 0), " +
            "         ri.material_code, ri.material_name, r.customer_name, ri.spec, ri.process_name")
    List<Map<String, Object>> aggregateReceiptQty();

    /**
     * 聚合发货明细：按(material_id, customer_id, process_id)汇总发货数量
     */
    @Select("SELECT " +
            "  si.material_id, " +
            "  s.customer_id, " +
            "  COALESCE(si.process_id, 0) AS process_id, " +
            "  SUM(si.quantity) AS ship_qty, " +
            "  MAX(s.shipment_date) AS last_ship_date " +
            "FROM shipment_item si " +
            "JOIN shipment s ON s.id = si.shipment_id " +
            "WHERE si.deleted = 0 AND s.deleted = 0 AND si.material_id IS NOT NULL " +
            "GROUP BY si.material_id, s.customer_id, COALESCE(si.process_id, 0)")
    List<Map<String, Object>> aggregateShipmentQty();
}