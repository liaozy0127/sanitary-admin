package com.sanitary.admin.util;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 单号生成工具
 * 格式：前缀 + yyyyMMdd + 4位序号，如 RH202603070001
 */
@Component
@RequiredArgsConstructor
public class GenerateNoUtil {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 生成单号（日期维度，每天重置序号）
     * @param prefix 前缀，如 RH、FH、PC、FG、SK
     * @param tableName 表名
     * @param noColumn 单号列名
     * @return 生成的单号
     */
    public synchronized String generate(String prefix, String tableName, String noColumn) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String likePattern = prefix + today + "%";
        String sql = "SELECT MAX(" + noColumn + ") FROM `" + tableName + "` WHERE " + noColumn + " LIKE ?";
        String maxNo = jdbcTemplate.queryForObject(sql, String.class, likePattern);
        int seq = 1;
        if (maxNo != null && maxNo.length() >= prefix.length() + 8 + 4) {
            try {
                seq = Integer.parseInt(maxNo.substring(maxNo.length() - 4)) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }
        return prefix + today + String.format("%04d", seq);
    }

    /**
     * 生成对账单号（全局唯一，不按月份重置序号）
     * 格式：前缀 + yyyyMM + 4位序号，如 DZ2026030001
     * @param prefix 前缀，如 DZ
     * @param tableName 表名
     * @param noColumn 单号列名
     * @return 生成的单号
     */
    public synchronized String generateMonthly(String prefix, String tableName, String noColumn) {
        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        // 查询全表最大值，确保全局唯一，而不是按月份LIKE查询
        String sql = "SELECT MAX(" + noColumn + ") FROM `" + tableName + "` WHERE " + noColumn + " LIKE ?";
        String likePattern = prefix + "______%"; // 匹配 DZ + 6位年月 + 序号，如 DZ202603xxxx
        String maxNo = jdbcTemplate.queryForObject(sql, String.class, likePattern);
        int seq = 1;
        if (maxNo != null && maxNo.length() >= prefix.length() + 6 + 4) {
            try {
                seq = Integer.parseInt(maxNo.substring(maxNo.length() - 4)) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }
        return prefix + yearMonth + String.format("%04d", seq);
    }
}
