package org.ssdd.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.Map;

/** Test-only mapper exercises the real MyBatis-Plus pagination interceptor. */
@Mapper
public interface UserPaginationMapper {
    @Select("SELECT id, email FROM users ORDER BY created_at, id")
    IPage<Map<String, Object>> pageUsers(Page<Map<String, Object>> page);
}
