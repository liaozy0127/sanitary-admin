package com.sanitary.admin.service.impl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.StatementItem;
import com.sanitary.admin.mapper.StatementItemMapper;
import com.sanitary.admin.service.StatementItemService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class StatementItemServiceImpl extends ServiceImpl<StatementItemMapper, StatementItem> implements StatementItemService {
    @Override
    public List<StatementItem> getByStatementId(Long statementId) {
        return list(new LambdaQueryWrapper<StatementItem>().eq(StatementItem::getStatementId, statementId));
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