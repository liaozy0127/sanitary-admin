package com.sanitary.admin.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.StatementItem;
import java.util.List;

public interface StatementItemService extends IService<StatementItem> {
    List<StatementItem> getByStatementId(Long statementId);
    boolean saveItems(Long statementId, String statementNo, List<StatementItem> items);
    boolean deleteByStatementId(Long statementId);
}