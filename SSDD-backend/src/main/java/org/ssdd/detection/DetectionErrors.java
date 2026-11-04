package org.ssdd.detection;

import org.springframework.http.HttpStatus;
import org.ssdd.common.exception.BusinessException;

public final class DetectionErrors {
    private DetectionErrors() {}
    public static BusinessException bad(String message) { return new BusinessException(HttpStatus.BAD_REQUEST,message); }
    public static BusinessException missing() { return new BusinessException(HttpStatus.NOT_FOUND,"检测记录或资源不存在"); }
    public static BusinessException conflict() { return new BusinessException(HttpStatus.CONFLICT,"幂等键已被不同内容使用或请求正在处理中"); }
    public static BusinessException busy() { return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,"检测繁忙，请稍后重试"); }
    public static BusinessException timeout() { return new BusinessException(HttpStatus.GATEWAY_TIMEOUT,"检测超出处理预算"); }
}
