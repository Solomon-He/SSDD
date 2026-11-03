package org.ssdd.common.web;

import jakarta.servlet.RequestDispatcher;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssdd.common.api.ApiResponse;
import org.ssdd.common.exception.GlobalExceptionHandler;

@RestController
@Hidden
public class FallbackErrorController implements ErrorController {
    @RequestMapping("/error")
    ResponseEntity<ApiResponse<Void>> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer code && code >= 400 && code <= 599 ? code : 500;
        return ResponseEntity.status(status).body(ApiResponse.failure(GlobalExceptionHandler.safeMessage(status)));
    }
}
