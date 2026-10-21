package com.hgx.hgxaiagent.tools;

import com.hgx.hgxaiagent.map.service.MapSpatialQueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 空间地图工具：让智能体可以查询某个地点（险村、监测站等）周边的
 * 空间对象分布（监测站、河段、流域、安置点等），结果以地图卡片形式展示在对话中。
 */
public class MapSpatialTool {

    private final MapSpatialQueryService mapSpatialQueryService;

    public MapSpatialTool(MapSpatialQueryService mapSpatialQueryService) {
        this.mapSpatialQueryService = mapSpatialQueryService;
    }

    @Tool(description = """
            当用户想在地图上查看某个地点（如某个险村、村子、监测站、河道）的位置，
            或了解该地点周边的空间对象分布（周边监测站、河段、流域、安置点、危险区等）时调用。
            返回结果中的 [MAP:结果ID] 标记必须原样保留在回答中，前端会据此在对话里渲染可收放的地图卡片。
            """)
    public String showSurroundingsOnMap(
            @ToolParam(description = "地点名称，例如 张家坟村、永定河") String placeName,
            @ToolParam(description = "周边范围半径，单位公里，默认 5") Double radiusKm) {
        double radius = radiusKm == null || radiusKm <= 0 || radiusKm > 50 ? 5 : radiusKm;
        MapSpatialQueryService.SpatialQueryOutcome outcome =
                mapSpatialQueryService.querySurroundings(placeName, radius);
        if (outcome.resultId() == null) {
            return outcome.summary();
        }
        return outcome.summary() + "\n地图已生成：[MAP:" + outcome.resultId() + "]";
    }
}
