package com.hgx.hgxaiagent.map.controller;

import com.hgx.hgxaiagent.map.model.MapMeta;
import com.hgx.hgxaiagent.map.service.MapService;
import com.hgx.hgxaiagent.map.service.MapSpatialQueryService;
import com.hgx.hgxaiagent.user.constant.UserConstant;
import com.hgx.hgxaiagent.user.model.User;
import com.hgx.hgxaiagent.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 地图空间数据接口。
 * 数据存储在 PostgreSQL/PostGIS，输出标准 GeoJSON。
 */
@RestController
@RequestMapping("/map")
public class MapController {

    private final MapService mapService;
    private final MapSpatialQueryService mapSpatialQueryService;
    private final UserService userService;

    public MapController(MapService mapService, MapSpatialQueryService mapSpatialQueryService, UserService userService) {
        this.mapService = mapService;
        this.mapSpatialQueryService = mapSpatialQueryService;
        this.userService = userService;
    }

    /**
     * 地图元信息：图层列表、要素数量、全图范围。
     */
    @GetMapping("/meta")
    public MapMeta getMapMeta() {
        return mapService.getMapMeta();
    }

    /**
     * 指定图层的全部要素（GeoJSON FeatureCollection）。
     */
    @GetMapping(value = "/layers/{layerId}/features", produces = "application/geo+json")
    public ResponseEntity<String> getLayerFeatures(@PathVariable String layerId) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/geo+json"))
                .body(mapService.getLayerFeaturesGeoJson(layerId));
    }

    /**
     * 对话内空间查询结果（由智能体工具生成，按 ID 拉取渲染地图卡片）。
     */
    @GetMapping("/query/{resultId}")
    public ResponseEntity<String> getSpatialQueryResult(@PathVariable String resultId) {
        String result = mapSpatialQueryService.getResult(resultId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "地图结果不存在或已过期");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(result);
    }

    /**
     * 导入 GeoJSON 为一个图层（管理员）。
     */
    @PostMapping("/layers/import")
    public Map<String, Object> importLayer(
            @RequestParam("layerId") String layerId,
            @RequestParam("layerName") String layerName,
            @RequestParam(value = "color", required = false) String color,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request) {
        assertAdmin(request);
        int count;
        try {
            count = mapService.importLayer(layerId, layerName, color, file);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return Map.of("layerId", layerId, "featureCount", count);
    }

    @DeleteMapping("/layers/{layerId}")
    public void deleteLayer(@PathVariable String layerId, HttpServletRequest request) {
        assertAdmin(request);
        mapService.deleteLayer(layerId);
    }

    private void assertAdmin(HttpServletRequest request) {
        User loginUser = userService.getCurrentUser(request);
        if (loginUser == null || loginUser.getUserRole() == null || loginUser.getUserRole() != UserConstant.ADMIN_ROLE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以管理地图图层");
        }
    }
}
