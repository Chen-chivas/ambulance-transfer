package com.ambtransfer.exception;

import com.ambtransfer.dto.response.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException exception) {
        return ResponseEntity.status(exception.status).body(new ApiError(exception.code, exception.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class)
    public ResponseEntity<ApiError> duplicate(org.springframework.dao.DuplicateKeyException exception) {
        return ResponseEntity.status(409).body(new ApiError("DUPLICATE_RECORD", "许可证号、车牌或登录名已存在，请检查后重试"));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(org.springframework.dao.DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(new ApiError("RECORD_IN_USE", "记录有关联资料或历史业务，无法执行此操作"));
    }

    @ExceptionHandler({org.springframework.web.method.annotation.HandlerMethodValidationException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> invalidParameter(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "查询参数不合法"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> business(BusinessException exception) {
        return ResponseEntity.status(409).body(new ApiError("BUSINESS_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> validation(Exception exception) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "请求参数不合法"));
    }

    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public ResponseEntity<ApiError> database(org.springframework.dao.DataAccessException exception) {
        LOG.error("Database operation failed", exception);
        return ResponseEntity.status(503).body(new ApiError("DATABASE_UNAVAILABLE", "数据库服务暂不可用"));
    }
}
