package com.sanitary.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.entity.Statement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface StatementMapper extends BaseMapper<Statement> {

    /**
     * 分页查询对账单（关联客户表获取最新客户名称）
     */
    @Select("<script>" +
            "SELECT s.id, s.statement_no, s.statement_month, s.customer_id, " +
            "  COALESCE(c.customer_name, s.customer_name) AS customer_name, " +
            "  s.receipt_qty, s.shipment_qty, s.receipt_amount, s.goods_amount, s.shipment_amount, " +
            "  s.remark, s.create_time, s.update_time " +
            "FROM statement s " +
            "LEFT JOIN customer c ON c.id = s.customer_id " +
            "WHERE s.deleted = 0 " +
            "<if test='customerId != null'> AND s.customer_id = #{customerId} </if>" +
            "<if test='statementMonth != null and statementMonth != \"\"'> AND s.statement_month = #{statementMonth} </if>" +
            "ORDER BY s.statement_month DESC, s.id DESC" +
            "</script>")
    IPage<Map<String, Object>> pageListWithJoin(Page<?> page, @Param("customerId") Long customerId, @Param("statementMonth") String statementMonth);
}
