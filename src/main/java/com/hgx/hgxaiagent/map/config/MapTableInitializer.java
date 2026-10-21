package com.hgx.hgxaiagent.map.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 地图空间数据表初始化。
 * 依赖 PostgreSQL 的 PostGIS 扩展（由数据库初始化脚本创建）。
 */
@Slf4j
@Component
public class MapTableInitializer {

    private final JdbcTemplate jdbcTemplate;

    public MapTableInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        // 图层元数据表：一个图层对应一类空间对象（险村、监测站、转移路线等）
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS map_layer (
                    id VARCHAR(64) PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    geometry_type VARCHAR(32) NOT NULL DEFAULT 'Point',
                    color VARCHAR(16) NOT NULL DEFAULT '#2f80ed',
                    sort_order INT NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL DEFAULT now()
                )
                """);
        // 空间要素表：geom 为 PostGIS geometry，SRID 4326（WGS84/CGCS2000 经纬度）
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS map_feature (
                    id BIGSERIAL PRIMARY KEY,
                    layer_id VARCHAR(64) NOT NULL REFERENCES map_layer(id) ON DELETE CASCADE,
                    name VARCHAR(255),
                    properties JSONB NOT NULL DEFAULT '{}'::jsonb,
                    geom geometry(Geometry, 4326) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_map_feature_layer ON map_feature (layer_id)
                """);
        // 空间索引（GiST），空间查询（范围内检索、相交判断）走索引
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_map_feature_geom ON map_feature USING GIST (geom)
                """);
        log.info("地图空间数据表初始化完成");
    }
}
