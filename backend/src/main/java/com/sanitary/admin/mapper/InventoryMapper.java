package com.sanitary.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /** 更新返工库存：收货(+qty)，发货时降至0但不低于0 */
    @Update("UPDATE inventory SET rework_qty = GREATEST(0, rework_qty + #{changeQty}), update_time = NOW() WHERE material_id = #{materialId} AND customer_id = #{customerId} AND process_id = #{processId}")
    int incrementReworkQty(@Param("materialId") Long materialId, @Param("customerId") Long customerId, @Param("processId") Long processId, @Param("changeQty") BigDecimal changeQty);

    /**
     * 分页查询库存（关联客户、物料、工艺表获取最新信息）
     */
    @Select("<script>" +
            "SELECT i.id, i.material_id, i.customer_id, i.process_id, " +
            "  COALESCE(m.material_code, i.material_code) AS material_code, " +
            "  COALESCE(m.material_name, i.material_name) AS material_name, " +
            "  COALESCE(c.customer_name, i.customer_name) AS customer_name, " +
            "  COALESCE(m.spec, i.spec) AS spec, " +
            "  COALESCE(p.process_name, i.process_name) AS process_name, " +
            "  i.quantity, i.rework_qty, i.last_receive_time, i.last_ship_time, i.create_time, i.update_time " +
            "FROM inventory i " +
            "LEFT JOIN material m ON m.id = i.material_id " +
            "LEFT JOIN customer c ON c.id = i.customer_id " +
            "LEFT JOIN process p ON p.id = i.process_id " +
            "WHERE i.quantity &gt; 0 " +
            "<if test='customerId != null'> AND i.customer_id = #{customerId} </if>" +
            "<if test='keyword != null and keyword != \"\"'> " +
            "  AND (COALESCE(m.material_code, i.material_code) LIKE CONCAT('%', #{keyword}, '%') " +
            "       OR COALESCE(m.material_name, i.material_name) LIKE CONCAT('%', #{keyword}, '%') " +
            "       OR COALESCE(c.customer_name, i.customer_name) LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY i.update_time DESC, i.id DESC" +
            "</script>")
    IPage<Map<String, Object>> pageListWithJoin(Page<?> page, @Param("customerId") Long customerId, @Param("keyword") String keyword);

    /**
     * 聚合收货明细：按(material_id, customer_id, process_id)汇总收货数量及维度信息
     */
    @Select("SELECT " +
            "  ri.material_id, " +
            "  r.customer_id, " +
            "  COALESCE(ri.process_id, 0) AS process_id, " +
            "  MAX(ri.material_code) AS material_code, " +
            "  MAX(ri.material_name) AS material_name, " +
            "  MAX(r.customer_name) AS customer_name, " +
            "  MAX(ri.spec) AS spec, " +
            "  MAX(ri.process_name) AS process_name, " +
            "  SUM(ri.quantity) AS receipt_qty, " +
            "  MAX(r.receipt_date) AS last_receive_date " +
            "FROM receipt_item ri " +
            "JOIN receipt r ON r.id = ri.receipt_id " +
            "WHERE ri.deleted = 0 AND r.deleted = 0 AND r.status = 1 AND ri.material_id IS NOT NULL " +
            "GROUP BY ri.material_id, r.customer_id, COALESCE(ri.process_id, 0)")
    List<Map<String, Object>> aggregateReceiptQty();

    /**
     * 聚合发货明细：按(material_id, customer_id, process_id)汇总发货数量（良品+废品）
     */
    @Select("SELECT " +
            "  si.material_id, " +
            "  s.customer_id, " +
            "  COALESCE(si.process_id, 0) AS process_id, " +
            "  SUM(si.quantity + COALESCE(si.defective_qty, 0)) AS ship_qty, " +
            "  MAX(s.shipment_date) AS last_ship_date " +
            "FROM shipment_item si " +
            "JOIN shipment s ON s.id = si.shipment_id " +
            "WHERE si.deleted = 0 AND s.deleted = 0 AND s.status = 1 AND si.material_id IS NOT NULL " +
            "GROUP BY si.material_id, s.customer_id, COALESCE(si.process_id, 0)")
    List<Map<String, Object>> aggregateShipmentQty();

    /**
     * 查询所有收货明细（用于重建流水）
     */
    @Select("SELECT " +
            "  ri.id, ri.material_id, r.customer_id, COALESCE(ri.process_id, 0) AS process_id, " +
            "  ri.material_code, ri.material_name, r.customer_name, ri.spec, ri.process_name, " +
            "  ri.quantity, ri.receipt_source, r.id AS order_id, r.receipt_no AS order_no, r.receipt_date AS order_date " +
            "FROM receipt_item ri " +
            "JOIN receipt r ON r.id = ri.receipt_id " +
            "WHERE ri.deleted = 0 AND r.deleted = 0 AND r.status = 1 AND ri.material_id IS NOT NULL " +
            "ORDER BY r.receipt_date, r.id, ri.id")
    List<Map<String, Object>> listAllReceiptItems();

    /**
     * 查询所有发货明细（用于重建流水）
     */
    @Select("SELECT " +
            "  si.id, si.material_id, s.customer_id, COALESCE(si.process_id, 0) AS process_id, " +
            "  si.material_code, si.material_name, s.customer_name, si.spec, si.process_name, " +
            "  (si.quantity + COALESCE(si.defective_qty, 0)) AS total_qty, " +
            "  s.id AS order_id, s.shipment_no AS order_no, s.shipment_date AS order_date " +
            "FROM shipment_item si " +
            "JOIN shipment s ON s.id = si.shipment_id " +
            "WHERE si.deleted = 0 AND s.deleted = 0 AND s.status = 1 AND si.material_id IS NOT NULL " +
            "ORDER BY s.shipment_date, s.id, si.id")
    List<Map<String, Object>> listAllShipmentItems();
}