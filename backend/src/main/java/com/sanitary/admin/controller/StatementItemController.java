package com.sanitary.admin.controller;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.StatementItem;
import com.sanitary.admin.service.StatementItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/statement-items")
@RequiredArgsConstructor
public class StatementItemController {
    private final StatementItemService statementItemService;

    @GetMapping
    public Result<List<StatementItem>> list(@RequestParam Long statementId) {
        return Result.success(statementItemService.getByStatementId(statementId));
    }
}