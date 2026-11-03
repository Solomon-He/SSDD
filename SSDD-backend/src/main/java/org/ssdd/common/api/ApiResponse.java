package org.ssdd.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, String message, String error) {
    public static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, data, null, null); }
    public static <T> ApiResponse<T> ok(T data, String message) { return new ApiResponse<>(true, data, message, null); }
    public static ApiResponse<Void> failure(String error) { return new ApiResponse<>(false, null, null, error); }
}
