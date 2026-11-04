package org.ssdd.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean
    public OpenAPI ssddOpenApi(@Value("${ssdd.version}") String version) {
        return new OpenAPI()
                .info(new Info().title("SSDD 后端接口").version(version)
                        .description("钢铁表面缺陷检测系统，仅支持 V2 模型。已实现账号权限、模型管理、CPU/CUDA 单张与同步批量检测、历史与签名资源、CSV 导出、管理员统计缓存及操作日志。"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
