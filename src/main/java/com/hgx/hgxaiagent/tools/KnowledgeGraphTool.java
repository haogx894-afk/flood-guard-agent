package com.hgx.hgxaiagent.tools;

import com.hgx.hgxaiagent.knowledgegraph.model.GraphCountItem;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphStats;
import com.hgx.hgxaiagent.knowledgegraph.service.KnowledgeGraphService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.stream.Collectors;

public class KnowledgeGraphTool {

    private final KnowledgeGraphService knowledgeGraphService;

    public KnowledgeGraphTool(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @Tool(description = """
            查询本地 Neo4j 知识图谱。
            当用户询问实体关系、上下游、所属行政区、所属流域、包含关系、负责人、监测关系、到达关系、关联对象、图谱统计等问题时，优先调用本工具。
            本工具适合回答：某村属于哪里、某流域包含哪些对象、某对象上下游是什么、某对象由谁负责、某类实体有多少个。
            """)
    public String searchKnowledgeGraph(
            @ToolParam(description = "用户问题或实体关键词，例如 张家坟村、永定河、安置点、危险区") String query) {
        return knowledgeGraphService.buildGraphContext(query, 2, 50);
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
