package org.ssdd.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import org.ssdd.common.api.*;
import org.ssdd.common.exception.*;
import org.ssdd.common.web.*;
import org.ssdd.config.*;
import org.ssdd.health.HealthController;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HealthController.class, properties = {
        "ssdd.version=test", "ssdd.database.username=unused", "ssdd.database.password=unused",
        "ssdd.cors.allowed-origins[0]=http://localhost:5173",
        "logging.level.org.ssdd.common.exception.GlobalExceptionHandler=OFF"})
@Import({WebContractTests.ContractController.class, GlobalExceptionHandler.class,
        RequestIdFilter.class, WebConfiguration.class, FallbackErrorController.class})
@EnableConfigurationProperties(CorsProperties.class)
class WebContractTests {
    @Autowired MockMvc mvc;

    @Test
    void preservesPaginationKeysDefaultsAndEmptyPageSemantics() throws Exception {
        mvc.perform(get("/api/contract/page"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items").isEmpty()).andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10)).andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.page_size").doesNotExist());
    }

    @Test
    void rejectsInvalidPaginationAndMalformedJsonWithUniformBody() throws Exception {
        for (String size : List.of("0", "101", "not-a-number")) {
            mvc.perform(get("/api/contract/page").param("pageSize", size))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error").value("请求参数错误"));
        }
        mvc.perform(get("/api/contract/page").param("page", "0")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/contract/body").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("请求参数错误"));
        mvc.perform(post("/api/contract/body").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void preservesCamelCaseRequestAndSafeBusinessErrors() throws Exception {
        mvc.perform(post("/api/contract/body").contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"质检员\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("质检员"));
        mvc.perform(get("/api/contract/conflict")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("名称已存在"));
        var response = mvc.perform(get("/api/contract/unexpected")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("服务器内部错误")).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("TEST_SECRET"));
        assertFalse(response.contains("trace"));
        mvc.perform(get("/api/contract/check-violation")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("资源冲突"));
    }

    @Test
    void normalizesFrameworkAndServletErrors() throws Exception {
        mvc.perform(put("/health")).andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/api/missing")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("资源不存在"));
        mvc.perform(get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 413))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void propagatesSafeRequestIdAndRestoresThreadContext() throws Exception {
        MDC.put("requestId", "outer-context");
        try {
            mvc.perform(get("/health").header("X-Request-Id", "client.request-123"))
                    .andExpect(header().string("X-Request-Id", "client.request-123"));
            assertEquals("outer-context", MDC.get("requestId"));
            String generated = mvc.perform(get("/health").header("X-Request-Id", "untrusted value"))
                    .andReturn().getResponse().getHeader("X-Request-Id");
            assertNotNull(generated);
            assertTrue(generated.matches("[0-9a-f-]{36}"));
        } finally { MDC.remove("requestId"); }
    }

    @Test
    void allowsConfiguredCorsOriginAndRejectsOthers() throws Exception {
        mvc.perform(options("/api/contract/page").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,idempotency-key"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/contract/page").header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @RestController
    static class ContractController {
        @GetMapping("/api/contract/page")
        ApiResponse<PageResult<String>> page(@Valid @ModelAttribute PageQuery query) {
            return ApiResponse.ok(PageResult.of(List.of(), 0, query.page(), query.pageSize()));
        }
        @PostMapping("/api/contract/body")
        ApiResponse<Payload> body(@Valid @RequestBody Payload payload) { return ApiResponse.ok(payload); }
        @GetMapping("/api/contract/conflict")
        void conflict() { throw new BusinessException(HttpStatus.CONFLICT, "名称已存在"); }
        @GetMapping("/api/contract/unexpected")
        void unexpected() { throw new IllegalStateException("TEST_SECRET must never appear in response"); }
        @GetMapping("/api/contract/check-violation")
        void checkViolation() {
            throw new org.springframework.jdbc.UncategorizedSQLException("insert", "private SQL",
                    new java.sql.SQLException("private constraint detail", "HY000", 3819));
        }
        record Payload(@NotBlank String displayName) {}
    }
}
