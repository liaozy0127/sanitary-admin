package com.sanitary.admin.common;

import lombok.Data;
import java.util.List;

@Data
public class PageResult<T> {
    private Long total;
    private List<T> records;

    public static <T> PageResult<T> of(Long total, List<T> records) {
        PageResult<T> page = new PageResult<>();
        page.setTotal(total);
        page.setRecords(records);
        return page;
    }
}
