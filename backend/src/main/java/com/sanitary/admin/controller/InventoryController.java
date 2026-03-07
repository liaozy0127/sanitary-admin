package com.sanitary.admin.controller;

import com.sanitary.admin.common.Result;
import com.sanitary.admin.vo.InventoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String keyword) {

        StringBuilder whereSql = new StringBuilder(" WHERE m.deleted = 0 AND m.status = 1 ");
        List<Object> params = new ArrayList<>();

        if (customerId != null) {
            whereSql.append(" AND m.customer_id = ? ");
            params.add(customerId);
        }
        if (StringUtils.hasText(keyword)) {
            whereSql.append(" AND (m.material_name LIKE ? OR m.material_code LIKE ?) ");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }

        String baseSql = """
                SELECT m.customer_id as customerId,
                       COALESCE(c.customer_name, m.customer_name) as customerName,
                       m.material_code as materialCode,
                       m.material_name as materialName,
                       m.spec,
                       COALESCE((SELECT SUM(r.quantity) FROM receipt r WHERE r.material_id = m.id AND r.deleted = 0 AND r.status = 1), 0) as receiptQty,
                       COALESCE((SELECT SUM(s.quantity) FROM shipment s WHERE s.material_id = m.id AND s.deleted = 0 AND s.status = 1), 0) as shipmentQty,
                       COALESCE((SELECT SUM(r.quantity) FROM receipt r WHERE r.material_id = m.id AND r.deleted = 0 AND r.status = 1), 0) -
                       COALESCE((SELECT SUM(s.quantity) FROM shipment s WHERE s.material_id = m.id AND s.deleted = 0 AND s.status = 1), 0) as currentStock,
                       m.default_price as lastPrice
                FROM material m
                LEFT JOIN customer c ON m.customer_id = c.id
                """;

        String havingSql = """ 
                HAVING (receiptQty > 0 OR shipmentQty > 0)
                ORDER BY customerName, materialCode
                """;

        // Count
        String countSql = "SELECT COUNT(*) FROM (" + baseSql + whereSql + ") tmp";
        // We need to count after HAVING. Let's count differently.
        String countInnerSql = "SELECT COUNT(*) FROM (" + baseSql + whereSql + havingSql + ") AS counted";

        // For counting with HAVING we wrap again
        String listSql = baseSql + whereSql + havingSql + " LIMIT ? OFFSET ?";
        List<Object> listParams = new ArrayList<>(params);
        listParams.add(size);
        listParams.add((page - 1) * size);

        List<InventoryVO> records = jdbcTemplate.query(listSql, BeanPropertyRowMapper.newInstance(InventoryVO.class),
                listParams.toArray());

        // Total count
        String totalSql = "SELECT COUNT(*) FROM (" + baseSql + whereSql + havingSql + ") AS total_count";
        Long total = jdbcTemplate.queryForObject(totalSql, Long.class, params.toArray());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total != null ? total : 0);
        result.put("current", page);
        result.put("size", size);

        return Result.success(result);
    }
}
