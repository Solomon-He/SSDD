package org.ssdd.common.api;

import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.List;

public record PageResult<T>(List<T> items, long total, long page, long pageSize, long totalPages) {
    public PageResult {
        if (total < 0 || page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Invalid pagination values");
        }
        items = List.copyOf(items);
        totalPages = total / pageSize + (total % pageSize == 0 ? 0 : 1);
    }
    public static <T> PageResult<T> of(List<T> items, long total, long page, long pageSize) {
        return new PageResult<>(items, total, page, pageSize, 0);
    }
    public static <T> PageResult<T> from(IPage<T> result) {
        return of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }
}
