package com.sanitary.admin.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ExcelExportUtil {
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static CellStyle titleStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontName("宋体"); f.setBold(true); f.setFontHeightInPoints((short) 16);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0x2F,(byte)0x75,(byte)0xB6}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    public static CellStyle headerStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontName("宋体"); f.setBold(true); f.setFontHeightInPoints((short) 11);
        f.setColor(new XSSFColor(new byte[]{(byte)0x1F,(byte)0x38,(byte)0x64}, null));
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xD6,(byte)0xE4,(byte)0xF0}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setWrapText(true);
        setBorder(s);
        return s;
    }

    public static CellStyle masterRowStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontName("宋体"); f.setBold(true); f.setFontHeightInPoints((short) 11);
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xBD,(byte)0xD7,(byte)0xEE}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    public static CellStyle masterRowDateStyle(XSSFWorkbook wb) {
        CellStyle s = masterRowStyle(wb);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    public static CellStyle dataStyle(XSSFWorkbook wb, boolean even) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontName("宋体"); f.setFontHeightInPoints((short) 11);
        s.setFont(f);
        if (even) {
            s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xF5,(byte)0xF9,(byte)0xFF}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    public static CellStyle numStyle(XSSFWorkbook wb, boolean even) {
        CellStyle s = dataStyle(wb, even);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setDataFormat(wb.createDataFormat().getFormat("0.00"));
        return s;
    }

    public static CellStyle dateStyle(XSSFWorkbook wb, boolean even) {
        CellStyle s = dataStyle(wb, even);
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    public static CellStyle summaryStyle(XSSFWorkbook wb) {
        CellStyle s = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontName("宋体"); f.setBold(true); f.setFontHeightInPoints((short) 11);
        s.setFont(f);
        s.setFillForegroundColor(new XSSFColor(new byte[]{(byte)0xFF,(byte)0xF2,(byte)0xCC}, null));
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(s);
        return s;
    }

    public static CellStyle summaryNumStyle(XSSFWorkbook wb) {
        CellStyle s = summaryStyle(wb);
        s.setAlignment(HorizontalAlignment.RIGHT);
        s.setDataFormat(wb.createDataFormat().getFormat("0.00"));
        return s;
    }

    private static void setBorder(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    public static void setCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellStyle(style);
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof BigDecimal bd) {
            cell.setCellValue(bd.doubleValue());
        } else if (value instanceof Number num) {
            cell.setCellValue(num.doubleValue());
        } else if (value instanceof LocalDate ld) {
            cell.setCellValue(ld.format(DATE_FMT));
        } else if (value instanceof LocalDateTime ldt) {
            cell.setCellValue(ldt.format(DT_FMT));
        } else {
            cell.setCellValue(value.toString());
        }
    }

    public static void autoSize(Sheet sheet, int colCount) {
        for (int i = 0; i < colCount; i++) {
            sheet.autoSizeColumn(i);
            int w = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(Math.max(w + 512, 2048), 14400));
        }
    }

    public static void writeResponse(XSSFWorkbook wb, HttpServletResponse response, String filename) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encoded);
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
        wb.write(response.getOutputStream());
        wb.close();
    }

    public static void writeTitleRow(Sheet sheet, XSSFWorkbook wb, String title, int colCount) {
        Row row = sheet.createRow(0);
        row.setHeightInPoints(30);
        CellStyle s = titleStyle(wb);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(s);
        for (int i = 1; i < colCount; i++) {
            row.createCell(i).setCellStyle(s);
        }
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, colCount - 1));
    }

    public static void writeHeaderRow(Sheet sheet, XSSFWorkbook wb, String[] headers) {
        Row row = sheet.createRow(1);
        row.setHeightInPoints(20);
        CellStyle s = headerStyle(wb);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(s);
        }
    }

    public static String fmtDate(LocalDate d) { return d == null ? "" : d.format(DATE_FMT); }
    public static String fmtDateTime(LocalDateTime dt) { return dt == null ? "" : dt.format(DT_FMT); }
    public static double bd(BigDecimal v) { return v == null ? 0 : v.doubleValue(); }
}
