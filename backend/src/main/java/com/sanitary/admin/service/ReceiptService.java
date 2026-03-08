package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Receipt;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

public interface ReceiptService extends IService<Receipt> {
    Page<Receipt> pageList(int page, int size, String keyword, Long customerId,
                           String startDate, String endDate);
    Receipt createReceipt(Receipt receipt);
    Map<String, Object> importExcel(MultipartFile file, String mode);
    void exportTemplate(HttpServletResponse response);
}
