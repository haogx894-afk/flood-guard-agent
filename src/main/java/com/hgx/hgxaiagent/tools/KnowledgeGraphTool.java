package com.hgx.hgxaiagent.tools;

import com.hgx.hgxaiagent.knowledgegraph.model.GraphCountItem;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphStats;
import com.hgx.hgxaiagent.knowledgegraph.service.GraphReasoningService;
import com.hgx.hgxaiagent.knowledgegraph.service.KnowledgeGraphService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.stream.Collectors;

public class KnowledgeGraphTool {

    private final KnowledgeGraphService knowledgeGraphService;
    private final GraphReasoningService graphReasoningService;

    public KnowledgeGraphTool(KnowledgeGraphService knowledgeGraphService, GraphReasoningService graphReasoningService) {
        this.knowledgeGraphService = knowledgeGraphService;
        this.graphReasoningService = graphReasoningService;
    }

    @Tool(description = """
            查询本地 Neo4j 知识图谱。
            当用户询问实体关系、上下游、所属行政区、所属流域、包含关系、负责人、监测关系、到达关系、关联对象、图谱统计等问题时，优先调用本工具。
            本工具适合回答：某村属于哪里、某流域包含哪些对象、某对象上下游是什么、某对象由谁负责、某类实体有多少个。
            如果问题匹配“某区有哪些险村、某镇有哪些险村、某流域有哪些对象、某险村如何转移”等固定业务场景，应优先调用对应的专用图谱推理工具。
            """)
    public String searchKnowledgeGraph(
            @ToolParam(description = "用户问题或实体关键词，例如 张家坟村、永定河、安置点、危险区") String query) {
        return knowledgeGraphService.buildGraphContext(query, 2, 50);
    }

    @Tool(description = """
            按区级行政区查询险村，并按乡镇分组。
            当用户问“怀柔区有哪些险村”“某区险村有多少”“某区险村按乡镇分布”等问题时，必须优先调用本工具。
            本工具会推理隐藏路径：险村 -> 村/镇 -> 区。
            """)
    public String queryRiskVillagesByDistrict(
            @ToolParam(description = "区级行政区名称，例如 怀柔区、密云区、房山区") String districtName) {
        return graphReasoningService.queryRiskVillagesByDistrict(districtName);
    }

    @Tool(description = """
            按镇级行政区查询险村。
            当用户问“某镇有哪些险村”“某乡镇险村有多少”等问题时，调用本工具。
            本工具会推理隐藏路径：险村 -> 镇。
            """)
    public String queryRiskVillagesByTown(
            @ToolParam(description = "镇级行政区名称，例如 九渡河镇、桥梓镇、宝山镇") String townName) {
        return graphReasoningService.queryRiskVillagesByTown(townName);
    }

    @Tool(description = """
            查询某个山洪沟流域内的重点对象。
            当用户问“某流域内有哪些险村、危险区、安置点、监测站、桥梁、路涵、露营地、景区、企事业单位”等问题时，调用本工具。
            本工具会推理隐藏路径：对象 -> 山洪沟流域，或 山洪沟流域 -> 包含 -> 对象。
            """)
    public String queryObjectsByBasin(
            @ToolParam(description = "山洪沟流域名称或关键词") String basinName) {
        return graphReasoningService.queryObjectsByBasin(basinName);
    }

    @Tool(description = """
            查询某个山洪沟流域内的监测站及监测对象。
            当用户问“某流域有哪些雨量站、水位站、视频站、墒情站、报讯站”“某流域监测覆盖情况”等问题时，调用本工具。
            """)
    public String queryMonitoringByBasin(
            @ToolParam(description = "山洪沟流域名称或关键词") String basinName) {
        return graphReasoningService.queryMonitoringByBasin(basinName);
    }

    @Tool(description = """
            查询某个危险区影响或包含的对象。
            当用户问“某危险区有哪些险村、露营地、景区、企事业单位”等问题时，调用本工具。
            本工具会推理隐藏路径：对象 -> 危险区，或 危险区 -> 包含 -> 对象。
            """)
    public String queryObjectsByDangerZone(
            @ToolParam(description = "危险区名称或关键词") String dangerZoneName) {
        return graphReasoningService.queryObjectsByDangerZone(dangerZoneName);
    }

    @Tool(description = """
            查询某个险村的转移路线和安置点。
            当用户问“某险村怎么转移”“某村有哪些安置点”“某村转移路线到哪里”等问题时，调用本工具。
            本工具会推理隐藏路径：转移路线 -> 险村；转移路线 -> 安置点；安置点 -> 险村。
            """)
    public String queryEvacuationByVillage(
            @ToolParam(description = "险村名称或关键词，例如 张家坟村") String villageName) {
        return graphReasoningService.queryEvacuationByVillage(villageName);
    }

    @Tool(description = """
            查询某个险村包含的险户清单。
            当用户问“某险村有哪些险户”“某村有多少险户”“下辛庄村（永定河）有哪些险户”等问题时，必须优先调用本工具。
            本工具会推理隐藏路径：险村 -> 包含 -> 险户，并优先使用实体的 vid 字段作为返回名称。
            """)
    public String queryHouseholdsByRiskVillage(
            @ToolParam(description = "险村名称或关键词，例如 下辛庄村（永定河）、张家坟村") String villageName) {
        return graphReasoningService.queryHouseholdsByRiskVillage(villageName);
    }

    @Tool(description = """
            查询河段或险村的上游/下游影响链。
            当用户问“某河段上游/下游是什么”“某险村上游有哪些村”“某对象可能影响下游哪些对象”等问题时，调用本工具。
            """)
    public String queryUpDownstreamImpact(
            @ToolParam(description = "河段、险村或其他对象名称") String objectName) {
        return graphReasoningService.queryUpDownstreamImpact(objectName);
    }

    @Tool(description = """
            获取知识图谱隐藏关系推理规则。
            当你不确定该用哪个图谱推理工具时，先调用本工具理解可推理的路径类型。
            """)
    public String getGraphReasoningRules() {
        return graphReasoningService.getReasoningRules();
    }

    @Tool(description = """
            查询 Neo4j 知识图谱全量统计信息。
            当用户询问图谱中有多少实体、多少关系、有哪些实体类型、有哪些关系类型时，调用本工具。
            """)
    public String getKnowledgeGraphStats() {
        GraphStats stats = knowledgeGraphService.stats();

        String nodeLabels = stats.nodeLabels().stream()
                .limit(30)
                .map(this::formatCountItem)
                .collect(Collectors.joining("\n"));

        String relationshipTypes = stats.relationshipTypes().stream()
                .limit(30)
                .map(this::formatCountItem)
                .collect(Collectors.joining("\n"));

        return """
                GRAPH_STATS
                实体总数：%d
                关系总数：%d
                占位节点数：%d

                实体类型统计：
                %s

                关系类型统计：
                %s
                """.formatted(
                stats.totalNodes(),
                stats.totalRelationships(),
                stats.placeholderNodes(),
                nodeLabels,
                relationshipTypes
        );
    }

    private String formatCountItem(GraphCountItem item) {
        return "- " + item.name() + "：" + item.count();
    }
}
