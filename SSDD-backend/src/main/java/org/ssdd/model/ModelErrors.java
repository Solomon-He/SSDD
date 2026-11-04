package org.ssdd.model;

import org.springframework.http.HttpStatus;
import org.ssdd.common.exception.BusinessException;

public final class ModelErrors {
    private ModelErrors() {}
    public static BusinessException bad(String message) { return new BusinessException(HttpStatus.BAD_REQUEST, message); }
    public static BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND, "模型不存在"); }
    public static BusinessException busy() { return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "模型或推理资源忙，请稍后重试"); }
    public static BusinessException unavailable(String message) { return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, message); }
    public static BusinessException conflict() { return new BusinessException(HttpStatus.CONFLICT, "模型状态已变化，请刷新后重试"); }
}
