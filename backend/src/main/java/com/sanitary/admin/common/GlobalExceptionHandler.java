package com.sanitary.admin.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理资源不存在异常（返回 HTTP 404）
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Result<Void> handleResponseStatus(ResponseStatusException ex) {
        return Result.error(ex.getStatusCode().value(), ex.getReason());
    }

    /**
     * 处理 @Valid 校验失败（Bean Validation）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
        return Result.error(400, msg);
    }

    /**
     * 处理参数约束校验失败
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        return Result.error(400, ex.getMessage());
    }

    /**
     * 兜底处理
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleGeneral(Exception ex) {
        return Result.error(500, "服务器内部错误：" + ex.getMessage());
    }
}
