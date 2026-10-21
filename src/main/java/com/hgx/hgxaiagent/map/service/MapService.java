package com.hgx.hgxaiagent.map.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hgx.hgxaiagent.map.model.MapBounds;
import com.hgx.hgxaiagent.map.model.MapLayerMeta;
import com.hgx.hgxaiagent.map.model.MapMeta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 地图空间数据服务。
 * 存储：PostgreSQL + PostGIS（SRID 4326），输出：GeoJSON。
 */
@Slf4j
@Service
public class MapService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MapService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 地图元信息：图层列表 + 全图范围。
     */
    public MapMeta getMapMeta() {
        List<MapLayerMeta> layers = jdbcTemplate.query("""
                        SELECT l.id, l.name, l.geometry_type, l.color, l.sort_order,
                               (SELECT count(*) FROM map_feature f WHERE f.layer_id = l.id) AS feature_count
                        FROM map_layer l
                        ORDER BY l.sort_order, l.created_at
                        """,
                (rs, rowNum) -> new MapLayerMeta(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("geometry_type"),
                        rs.getString("color"),
                        rs.getInt("sort_order"),
                        rs.getLong("feature_count")
                ));

        MapBounds bounds = jdbcTemplate.query("""
                        SELECT ST_XMin(extent), ST_YMin(extent), ST_XMax(extent), ST_YMax(extent)
                        FROM (SELECT ST_Extent(geom) AS extent FROM map_feature) t
                        """,
                rs -> (rs.next() && rs.getObject(1) != null)
                        ? new MapBounds(rs.getDouble(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4))
                        : null);

        return new MapMeta(
                "1.0",
                "北京市山洪防御空间地图",
                "EPSG:4326",
                "空间数据存储于 PostgreSQL/PostGIS，接口输出 GeoJSON",
                OffsetDateTime.now().toString(),
                bounds,
                layers
        );
    }

    /**
     * 查询指定图层的全部要素，输出标准 GeoJSON FeatureCollection。
     * 几何列通过 ST_AsGeoJSON 直接由 PostGIS 序列化。
     */
    public String getLayerFeaturesGeoJson(String layerId) {
        List<ObjectNode> features = jdbcTemplate.query("""
                        SELECT id, name, properties::text AS properties, ST_AsGeoJSON(geom) AS geometry
                        FROM map_feature
                        WHERE layer_id = ?
                        ORDER BY id
                        """,
                (rs, rowNum) -> {
                    ObjectNode feature = objectMapper.createObjectNode();
                    feature.put("type", "Feature");
                    ObjectNode properties;
                    try {
                        properties = (ObjectNode) objectMapper.readTree(rs.getString("properties"));
                    } catch (Exception e) {
                        properties = objectMapper.createObjectNode();
                    }
                    properties.put("id", rs.getLong("id"));
                    if (rs.getString("name") != null) {
                        properties.put("name", rs.getString("name"));
                    }
                    feature.set("properties", properties);
                    try {
                        feature.set("geometry", objectMapper.readTree(rs.getString("geometry")));
                    } catch (Exception e) {
                        feature.set("geometry", objectMapper.nullNode());
                    }
                    return feature;
                }, layerId);

        ObjectNode collection = objectMapper.createObjectNode();
        collection.put("type", "FeatureCollection");
        ArrayNode array = collection.putArray("features");
        features.forEach(array::add);
        return collection.toString();
    }

    /**
     * 导入 GeoJSON 文件为一个图层（管理员）。
     * 图层 ID 已存在时拒绝导入——地图数据只允许新增和删除，不允许修改，
     * 如需更新请先删除旧图层再导入，保证数据血缘清晰。
     */
    @Transactional
    public int importLayer(String layerId, String layerName, String color, MultipartFile file) {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM map_layer WHERE id = ?", Integer.class, layerId);
        if (exists != null && exists > 0) {
            throw new IllegalStateException("图层「" + layerId + "」已存在。地图数据不允许修改，请先删除旧图层再导入。");
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(content);
            JsonNode featuresNode = root.get("features");
            if (featuresNode == null || !featuresNode.isArray()) {
                throw new IllegalArgumentException("文件不是合法的 GeoJSON FeatureCollection");
            }

            jdbcTemplate.update("""
                    INSERT INTO map_layer (id, name, geometry_type, color)
                    VALUES (?, ?, 'Mixed', ?)
                    """, layerId, layerName, color == null || color.isBlank() ? "#2f80ed" : color);

            int count = 0;
            for (JsonNode feature : featuresNode) {
                JsonNode geometry = feature.get("geometry");
                if (geometry == null || geometry.isNull()) {
                    continue;
                }
                JsonNode propsNode = feature.get("properties");
                String properties = propsNode != null && propsNode.isObject() ? propsNode.toString() : "{}";
                String name = propsNode != null && propsNode.hasNonNull("name")
                        ? propsNode.get("name").asText() : null;
                jdbcTemplate.update("""
                        INSERT INTO map_feature (layer_id, name, properties, geom)
                        VALUES (?, ?, ?::jsonb, ST_GeomFromGeoJSON(?))
                        """, layerId, name, properties, geometry.toString());
                count++;
            }
            log.info("图层 {} 导入完成，共 {} 个要素", layerId, count);
            return count;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("GeoJSON 解析或入库失败：" + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteLayer(String layerId) {
        jdbcTemplate.update("DELETE FROM map_layer WHERE id = ?", layerId);
    }

    public List<String> listLayerIds() {
        return jdbcTemplate.queryForList("SELECT id FROM map_layer ORDER BY sort_order, created_at", String.class);
    }
}
