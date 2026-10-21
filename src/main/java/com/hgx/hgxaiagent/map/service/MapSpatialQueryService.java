package com.hgx.hgxaiagent.map.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对话内空间查询服务。
 * 围绕指定地点做 PostGIS 缓冲区/邻域查询（ST_DWithin），
 * 结果暂存内存，前端通过结果 ID 拉取 GeoJSON 渲染地图卡片。
 */
@Slf4j
@Service
public class MapSpatialQueryService {

    /** 结果缓存有效期：2 小时 */
    private static final long RESULT_TTL_MILLIS = 2 * 60 * 60 * 1000L;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** resultId -> (结果 JSON, 过期时间戳)，单实例部署用内存缓存即可 */
    private final Map<String, CachedResult> resultCache = new ConcurrentHashMap<>();

    public MapSpatialQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private record CachedResult(String json, long expiresAt) {
    }

    /**
     * 查询指定地点周边各图层的空间对象，返回结果 ID 和人类可读摘要。
     *
     * @return 结果 ID；找不到地点时返回 null，summary 中带提示
     */
    public SpatialQueryOutcome querySurroundings(String placeName, double radiusKm) {
        // 1. 定位目标地点（名称模糊匹配，优先完全匹配）
        List<Map<String, Object>> candidates = jdbcTemplate.queryForList("""
                SELECT f.name, ST_X(ST_Centroid(f.geom)) AS lon, ST_Y(ST_Centroid(f.geom)) AS lat,
                       f.layer_id
                FROM map_feature f
                WHERE f.name ILIKE ?
                ORDER BY (f.name = ?) DESC, f.id
                LIMIT 1
                """, "%" + placeName + "%", placeName);

        if (candidates.isEmpty()) {
            return new SpatialQueryOutcome(null,
                    "未在空间数据中找到名为「" + placeName + "」的地点，请确认名称，或先由管理员导入对应图层的空间数据。");
        }

        Map<String, Object> place = candidates.get(0);
        String targetName = (String) place.get("name");
        double lon = ((Number) place.get("lon")).doubleValue();
        double lat = ((Number) place.get("lat")).doubleValue();
        double radiusMeters = radiusKm * 1000;

        // 2. 逐图层做邻域查询（geography 类型保证米制距离）
        List<Map<String, Object>> layers = jdbcTemplate.queryForList(
                "SELECT id, name, color FROM map_layer ORDER BY sort_order, created_at");

        ObjectNode bundle = objectMapper.createObjectNode();
        bundle.put("id", UUID.randomUUID().toString().substring(0, 8));
        bundle.put("title", targetName + " 周边 " + radiusKm + "km 空间对象");
        bundle.put("targetName", targetName);
        bundle.put("radiusKm", radiusKm);
        ObjectNode center = bundle.putObject("center");
        center.put("lon", lon);
        center.put("lat", lat);

        ArrayNode layersNode = bundle.putArray("layers");
        StringBuilder summary = new StringBuilder();
        summary.append("已查询「").append(targetName).append("」周边 ").append(radiusKm).append(" 公里范围：");

        for (Map<String, Object> layer : layers) {
            String layerId = (String) layer.get("id");
            List<ObjectNode> features = jdbcTemplate.query("""
                            SELECT f.id, f.name, f.properties::text AS properties, ST_AsGeoJSON(f.geom) AS geometry
                            FROM map_feature f
                            WHERE f.layer_id = ?
                              AND ST_DWithin(
                                    f.geom::geography,
                                    ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                                    ?)
                            ORDER BY f.id
                            LIMIT 500
                            """,
                    (rs, rowNum) -> toFeature(rs.getLong("id"), rs.getString("name"),
                            rs.getString("properties"), rs.getString("geometry")),
                    layerId, lon, lat, radiusMeters);

            if (features.isEmpty()) {
                continue;
            }
            ObjectNode layerNode = layersNode.addObject();
            layerNode.put("id", layerId);
            layerNode.put("name", (String) layer.get("name"));
            layerNode.put("color", (String) layer.get("color"));
            layerNode.put("featureCount", features.size());
            ObjectNode geojson = layerNode.putObject("geojson");
            geojson.put("type", "FeatureCollection");
            ArrayNode featuresArray = geojson.putArray("features");
            features.forEach(featuresArray::add);

            summary.append(layer.get("name")).append(' ').append(features.size()).append(" 个、");
        }

        if (layersNode.isEmpty()) {
            return new SpatialQueryOutcome(null,
                    "「" + targetName + "」周边 " + radiusKm + " 公里内暂无已导入的空间对象，可尝试扩大范围或先导入周边图层数据。");
        }

        summary.setLength(summary.length() - 1);
        summary.append('。');

        String resultId = bundle.get("id").asText();
        resultCache.put(resultId, new CachedResult(bundle.toString(),
                Instant.now().toEpochMilli() + RESULT_TTL_MILLIS));
        return new SpatialQueryOutcome(resultId, summary.toString());
    }

    /**
     * 按结果 ID 取查询结果（前端渲染用）。
     */
    public String getResult(String resultId) {
        CachedResult cached = resultCache.get(resultId);
        if (cached == null || cached.expiresAt() < Instant.now().toEpochMilli()) {
            resultCache.remove(resultId);
            return null;
        }
        return cached.json();
    }

    private ObjectNode toFeature(long id, String name, String propertiesJson, String geometryJson) {
        ObjectNode feature = objectMapper.createObjectNode();
        feature.put("type", "Feature");
        ObjectNode properties;
        try {
            properties = propertiesJson != null
                    ? (ObjectNode) objectMapper.readTree(propertiesJson)
                    : objectMapper.createObjectNode();
        } catch (Exception e) {
            properties = objectMapper.createObjectNode();
        }
        properties.put("id", id);
        if (name != null) {
            properties.put("name", name);
        }
        feature.set("properties", properties);
        try {
            feature.set("geometry", objectMapper.readTree(geometryJson));
        } catch (Exception e) {
            feature.set("geometry", objectMapper.nullNode());
        }
        return feature;
    }

    public record SpatialQueryOutcome(String resultId, String summary) {
    }
}
