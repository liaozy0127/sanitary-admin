package com.sanitary.admin.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sanitary.admin.entity.StatementItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface StatementItemMapper extends BaseMapper<StatementItem> {

    /**
     * 查询对账单明细（关联物料、工艺表获取最新信息）
     */
    @Select("SELECT si.id, si.statement_id, si.statement_no, si.material_id, si.process_id, " +
            "  si.prev_balance_qty, si.receipt_qty, si.rework_qty, si.shipment_qty, si.defective_qty, " +
            "  si.curr_balance_qty, si.unit_price, si.goods_amount, si.shipment_amount, si.remark, " +
            "  COALESCE(m.material_code, si.material_code) AS material_code, " +
            "  COALESCE(m.material_name, si.material_name) AS material_name, " +
            "  m.spec AS spec, " +
            "  COALESCE(p.process_name, si.process_name) AS process_name " +
            "FROM statement_item si " +
            "LEFT JOIN material m ON m.id = si.material_id " +
            "LEFT JOIN process p ON p.id = si.process_id " +
            "WHERE si.statement_id = #{statementId} AND si.deleted = 0 " +
            "ORDER BY si.id")
    List<Map<String, Object>> listByStatementIdWithJoin(@Param("statementId") Long statementId);
}