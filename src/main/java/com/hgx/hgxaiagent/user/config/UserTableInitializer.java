package com.hgx.hgxaiagent.user.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 用户表初始化。
 */
@Component
public class UserTableInitializer {

    private final JdbcTemplate jdbcTemplate;

    public UserTableInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS app_user (
                    id BIGSERIAL PRIMARY KEY,
                    username VARCHAR(256),
                    user_account VARCHAR(256) NOT NULL,
                    avatar_url VARCHAR(1024),
                    gender INTEGER,
                    user_password VARCHAR(512) NOT NULL,
                    phone VARCHAR(128),
                    email VARCHAR(512),
                    user_status INTEGER NOT NULL DEFAULT 0,
                    create_time TIMESTAMP NOT NULL DEFAULT now(),
                    update_time TIMESTAMP NOT NULL DEFAULT now(),
                    is_delete INTEGER NOT NULL DEFAULT 0,
                    user_role INTEGER NOT NULL DEFAULT 0
                )
                """);

        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_app_user_account_active
                ON app_user (user_account)
                WHERE is_delete = 0
                """);
    }
}
