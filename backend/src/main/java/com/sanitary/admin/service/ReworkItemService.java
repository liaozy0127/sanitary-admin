package com.sanitary.admin.service;

import com.sanitary.admin.entity.ReworkItem;
import java.util.List;

public interface ReworkItemService {
    List<ReworkItem> getByReworkId(Long reworkId);
    List<ReworkItem> listByReworkIds(List<Long> reworkIds);
    boolean saveItems(Long reworkId, String reworkNo, List<ReworkItem> items);
    boolean deleteByReworkId(Long reworkId);
}