package com.sanitary.admin.controller;

import com.sanitary.admin.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 月度汇总报表
     * 返回各客户本月收货总量/金额、发货总量/金额
     */
    @GetMapping("/monthly")
    public Result<List<Map<String, Object>>> monthly(
            @RequestParam int year,
            @RequestParam int month) {

        String startDate = String.format("%04d-%02d-01", year, month);
        String endDate = String.format("%04d-%02d-01", year == 12 ? year + 1 : year, month == 12 ? 1 : month + 1);

        String sql = """
                SELECT
                    c.customer_name as customerName,
                    COALESCE(r.receipt_qty, 0) as receiptQty,
                    COALESCE(r.receipt_amount, 0) as receiptAmount,
                    COALESCE(s.shipment_qty, 0) as shipmentQty,
                    COALESCE(s.shipment_amount, 0) as shipmentAmount
                FROM customer c
                LEFT JOIN (
                    SELECT customer_id,
                           SUM(quantity) as receipt_qty,
                           SUM(amount) as receipt_amount
                    FROM receipt
                    WHERE deleted = 0 AND status = 1
                      AND receipt_date >= ? AND receipt_date < ?
                    GROUP BY customer_id
                ) r ON c.id = r.customer_id
                LEFT JOIN (
                    SELECT customer_id,
                           SUM(quantity) as shipment_qty,
                           SUM(amount) as shipment_amount
                    FROM shipment
                    WHERE deleted = 0 AND status = 1
                      AND shipment_date >= ? AND shipment_date < ?
                    GROUP BY customer_id
                ) s ON c.id = s.customer_id
                WHERE c.deleted = 0 AND c.status = 1
                  AND (r.receipt_qty IS NOT NULL OR s.shipment_qty IS NOT NULL)
                ORDER BY c.customer_name
                """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql,
                startDate, endDate, startDate, endDate);

        return Result.success(results);
    }
}
