package org.ssdd.auth;

import org.apache.ibatis.annotations.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.time.LocalDateTime;

@Mapper
public interface AuthMapper {
    String USER = """
            SELECT u.id,u.email,u.password_hash,u.status,p.id AS profile_id,p.role,p.display_name,
                   p.avatar_storage_key,p.created_at,p.updated_at
            FROM users u JOIN user_profiles p ON p.user_id=u.id
            """;
    record UserRow(String id, String email, String passwordHash, String status, String profileId, String role,
                   String displayName, String avatarStorageKey, LocalDateTime createdAt, LocalDateTime updatedAt) {
        @Override public String toString() { return "UserRow[credentials=REDACTED]"; }
    }
    record TokenRow(String id, String sessionId, String userId, LocalDateTime expiresAt,
                    LocalDateTime consumedAt, LocalDateTime revokedAt) {}
    record SessionRow(String id, String userId, LocalDateTime expiresAt, LocalDateTime revokedAt) {}

    @Select(USER+" WHERE u.email=#{email} FOR UPDATE") UserRow lockByEmail(String email);
    @Select(USER+" WHERE u.id=#{id} FOR UPDATE") UserRow lockUser(String id);
    @Select(USER+" WHERE u.id=#{id}") UserRow user(String id);
    @Select(USER+" WHERE p.id=#{id}") UserRow byProfileId(String id);
    @Select(USER+" JOIN auth_sessions s ON s.user_id=u.id WHERE s.id=#{sid} AND u.id=#{uid} AND u.status='active' AND s.revoked_at IS NULL AND s.expires_at>UTC_TIMESTAMP(6)")
    UserRow activeSessionUser(@Param("sid") String sid, @Param("uid") String userId);
    @Insert("INSERT INTO users(id,email,password_hash) VALUES(#{id},#{email},#{hash})")
    void insertUser(@Param("id") String id,@Param("email") String email,@Param("hash") String hash);
    @Insert("INSERT INTO user_profiles(id,user_id,role,display_name) VALUES(#{id},#{uid},#{role},#{name})")
    void insertProfile(@Param("id") String id,@Param("uid") String userId,@Param("role") String role,@Param("name") String name);
    @Update("UPDATE users SET password_hash=#{hash} WHERE id=#{id}")
    void password(@Param("id") String id,@Param("hash") String hash);
    @Update("UPDATE user_profiles SET display_name=#{name},avatar_storage_key=#{avatar} WHERE user_id=#{id}")
    void profile(@Param("id") String id,@Param("name") String name,@Param("avatar") String avatar);
    @Insert("INSERT INTO auth_sessions(id,user_id,expires_at,ip_address,user_agent) VALUES(#{id},#{uid},#{expires},#{ip},#{agent})")
    void insertSession(@Param("id") String id,@Param("uid") String userId,@Param("expires") LocalDateTime expires,@Param("ip") String ip,@Param("agent") String agent);
    @Select("SELECT id,user_id,expires_at,revoked_at FROM auth_sessions WHERE id=#{id} FOR UPDATE") SessionRow lockSession(String id);
    @Update("UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND revoked_at IS NULL") void revokeSession(String id);
    @Update("UPDATE auth_sessions SET revoked_at=UTC_TIMESTAMP(6) WHERE user_id=#{id} AND revoked_at IS NULL") void revokeAll(String id);
    @Select("SELECT t.id,t.session_id,s.user_id,t.expires_at,t.consumed_at,t.revoked_at FROM auth_refresh_tokens t JOIN auth_sessions s ON s.id=t.session_id WHERE t.token_hash=#{hash}")
    TokenRow token(String hash);
    @Select("SELECT t.id,t.session_id,s.user_id,t.expires_at,t.consumed_at,t.revoked_at FROM auth_refresh_tokens t JOIN auth_sessions s ON s.id=t.session_id WHERE t.id=#{id} FOR UPDATE")
    TokenRow lockToken(String id);
    @Insert("INSERT INTO auth_refresh_tokens(id,session_id,token_hash,expires_at) VALUES(#{id},#{sid},#{hash},#{expires})")
    void insertToken(@Param("id") String id,@Param("sid") String sessionId,@Param("hash") String hash,@Param("expires") LocalDateTime expires);
    @Update("UPDATE auth_refresh_tokens SET consumed_at=UTC_TIMESTAMP(6),replaced_by=#{next} WHERE id=#{id}")
    void consume(@Param("id") String id,@Param("next") String next);
    @Select("SELECT lock_name FROM system_locks WHERE lock_name='admin-management' FOR UPDATE") String lockAdminManagement();
    @Update("UPDATE user_profiles SET role=#{role} WHERE user_id=#{id}") void role(@Param("id") String id,@Param("role") String role);
    @Select("SELECT COUNT(*) FROM users u JOIN user_profiles p ON p.user_id=u.id WHERE p.role='admin' AND u.status='active'") long activeAdmins();
    @Select("SELECT COUNT(*) FROM detection_records WHERE user_id=#{id}") long detectionCount(String id);
    @Select("""
            <script>
            """+USER+"""
            <where>
              <if test="search != null">AND (p.display_name LIKE CONCAT('%',#{search},'%') OR u.email LIKE CONCAT('%',#{search},'%'))</if>
              <if test="role != null">AND p.role=#{role}</if>
            </where>
            ORDER BY p.created_at DESC,p.id DESC
            </script>
            """)
    IPage<UserRow> page(Page<UserRow> page,@Param("search") String search,@Param("role") String role);
}
