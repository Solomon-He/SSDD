package org.ssdd.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.ssdd.common.api.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return super.handleExceptionInternal(ex, ApiResponse.failure(safeMessage(status.value())), headers, status, request);
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
        if (ex instanceof org.ssdd.ratelimit.RateLimitException limited) {
            return ResponseEntity.status(ex.getStatus()).header("Retry-After",Long.toString(limited.retryAfter()))
                    .body(ApiResponse.failure(ex.getMessage()));
        }
        return ResponseEntity.status(ex.getStatus()).body(ApiResponse.failure(ex.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiResponse<Void>> validation(ConstraintViolationException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(safeMessage(400)));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(safeMessage(409)));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiResponse<Void>> database(DataAccessException ex) {
        // MySQL reports CHECK violations as HY000/3819, which may remain uncategorized in Spring JDBC.
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && sql.getErrorCode() == 3819) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(safeMessage(409)));
            }
        }
        LOG.error("Database operation failed", ex);
        HttpStatus status = ex instanceof QueryTimeoutException ? HttpStatus.GATEWAY_TIMEOUT
                : ex instanceof DataAccessResourceFailureException || ex instanceof TransientDataAccessResourceException
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(ApiResponse.failure(safeMessage(status.value())));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception ex) {
        LOG.error("Unhandled request failure", ex);
        return ResponseEntity.internalServerError().body(ApiResponse.failure(safeMessage(500)));
    }

    public static String safeMessage(int status) {
        return switch (status) {
            case 400 -> "请求参数错误";
            case 401 -> "未认证或会话已过期";
            case 403 -> "权限不足";
            case 404 -> "资源不存在";
            case 405 -> "请求方法不支持";
            case 406 -> "无法返回所需的响应格式";
            case 409 -> "资源冲突";
            case 413 -> "上传内容超过大小限制";
            case 415 -> "不支持的请求内容类型";
            case 429 -> "请求过于频繁";
            case 503 -> "服务不可用";
            case 504 -> "处理超时";
            default -> status >= 500 ? "服务器内部错误" : "请求失败";
        };
    }
}
