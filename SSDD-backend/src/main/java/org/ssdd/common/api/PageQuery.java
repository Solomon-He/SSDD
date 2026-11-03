package org.ssdd.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PageQuery(@Min(1) Integer page, @Min(1) @Max(100) Integer pageSize) {
    public PageQuery {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
    }
    public <T> Page<T> toMybatisPage() { return new Page<>(page, pageSize); }
}
