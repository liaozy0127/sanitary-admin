package com.sanitary.admin.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.StatementItem;
import com.sanitary.admin.mapper.StatementItemMapper;
import com.sanitary.admin.service.StatementItemService;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class StatementItemServiceImpl extends ServiceImpl<StatementItemMapper, StatementItem> implements StatementItemService {
    @Override
    public List<StatementItem> getByStatementId(Long statementId) {
        // 使用关联查询获取最新的物料、工艺信息
        List<Map<String, Object>> mapList = getBaseMapper().listByStatementIdWithJoin(statementId);
        List<StatementItem> items = new ArrayList<>();
        for (Map<String, Object> map : mapList) {
            StatementItem item = new StatementItem();
            item.setId(toLong(map.get("id")));
            item.setStatementId(toLong(map.get("statement_id")));
            item.setStatementNo(str(map.get("statement_no")));
            item.setMaterialId(toLong(map.get("material_id")));
            item.setMaterialCode(str(map.get("material_code")));
            item.setMaterialName(str(map.get("material_name")));
            item.setSpec(str(map.get("spec")));
            item.setProcessId(toLong(map.get("process_id")));
            item.setProcessName(str(map.get("process_name")));
            item.setPrevBalanceQty(toBigDecimal(map.get("prev_balance_qty")));
            item.setReceiptQty(toBigDecimal(map.get("receipt_qty")));
            item.setNormalReceiptQty(toBigDecimal(map.get("normal_receipt_qty")));
            item.setReworkQty(toBigDecimal(map.get("rework_qty")));
            item.setShipmentQty(toBigDecimal(map.get("shipment_qty")));
            item.setGoodsShipQty(toBigDecimal(map.get("goods_ship_qty")));
            item.setDefectiveQty(toBigDecimal(map.get("defective_qty")));
            item.setCurrBalanceQty(toBigDecimal(map.get("curr_balance_qty")));
            item.setUnitPrice(toBigDecimal(map.get("unit_price")));
            item.setGoodsAmount(toBigDecimal(map.get("goods_amount")));
            item.setReworkAmount(toBigDecimal(map.get("rework_amount")));
            item.setPrevFinancialBalance(toBigDecimal(map.get("prev_financial_balance")));
            item.setPrevFinancialOrigin(str(map.get("prev_financial_origin")));
            item.setShipmentAmount(toBigDecimal(map.get("shipment_amount")));
            item.setRemark(str(map.get("remark")));
            items.add(item);
        }
        return items;
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Long) return (Long) v;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); }
        catch (Exception e) { return null; }
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal) return (BigDecimal) v;
        if (v instanceof Number) return new BigDecimal(v.toString());
        try { return new BigDecimal(v.toString()); }
        catch (Exception e) { return null; }
    }

    private String str(Object v) {
        return v == null ? null : v.toString();
    }

    @Override
    public boolean saveItems(Long statementId, String statementNo, List<StatementItem> items) {
        if (items == null || items.isEmpty()) return true;
        for (StatementItem item : items) {
            item.setStatementId(statementId);
            item.setStatementNo(statementNo);
            getBaseMapper().insert(item);
        }
        return true;
    }

    @Override
    public boolean deleteByStatementId(Long statementId) {
        return remove(new LambdaQueryWrapper<StatementItem>().eq(StatementItem::getStatementId, statementId));
    }
}