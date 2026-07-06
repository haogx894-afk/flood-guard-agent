package com.hgx.hgxaiagent.knowledgegraph.service;

import com.hgx.hgxaiagent.knowledgegraph.model.GraphCountItem;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphData;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphEdge;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphHealth;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphNode;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphSearchResult;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphStats;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Entity;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.neo4j.driver.Values.parameters;

@Service
public class KnowledgeGraphService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;
    private static final int DEFAULT_DEPTH = 1;
    private static final int MAX_DEPTH = 5;

    private final Driver driver;

    public KnowledgeGraphService(Driver driver) {
        this.driver = driver;
    }

    public GraphHealth health() {
        try (Session session = driver.session()) {
            session.run("RETURN 1 AS ok").single().get("ok").asInt();
            return new GraphHealth(true, "neo4j", "Neo4j connected");
        } catch (Exception e) {
            return new GraphHealth(false, "neo4j", e.getMessage());
        }
    }

    public GraphStats stats() {
        try (Session session = driver.session()) {
            long totalNodes = singleLong(session, "MATCH (n) RETURN count(n) AS total");
            long totalRelationships = singleLong(session, "MATCH ()-[r]->() RETURN count(r) AS total");
            long placeholderNodes = singleLong(session, "MATCH (n:MissingEndpoint) RETURN count(n) AS total");

            List<GraphCountItem> nodeLabels = session.run("""
                    MATCH (n)
                    UNWIND labels(n) AS label
                    RETURN label AS name, count(*) AS total
                    ORDER BY total DESC
                    """).list(record -> new GraphCountItem(
                    record.get("name").asString(),
                    record.get("total").asLong()
            ));

            List<GraphCountItem> relationshipTypes = session.run("""
                    MATCH ()-[r]->()
                    RETURN type(r) AS name, count(r) AS total
                    ORDER BY total DESC
                    """).list(record -> new GraphCountItem(
                    record.get("name").asString(),
                    record.get("total").asLong()
            ));

            return new GraphStats(totalNodes, totalRelationships, placeholderNodes, nodeLabels, relationshipTypes);
        }
    }

    public GraphSearchResult search(String keyword, int limit) {
        String safeKeyword = cleanKeyword(keyword);
        List<String> searchTerms = buildSearchTerms(safeKeyword);
        int safeLimit = safeLimit(limit);

        try (Session session = driver.session()) {
            long nodeCount = countSearchNodes(session, safeKeyword, searchTerms);
            long relationshipCount = countSearchRelationships(session, safeKeyword, searchTerms);
            List<GraphNode> nodes = querySearchNodes(session, safeKeyword, searchTerms, safeLimit);
            List<GraphEdge> edges = querySearchRelationships(session, safeKeyword, searchTerms, safeLimit);

            return new GraphSearchResult(safeKeyword, nodeCount, relationshipCount, nodes, edges);
        }
    }

    public GraphNode getNode(String nodeId) {
        if (!StringUtils.hasText(nodeId)) {
            throw new IllegalArgumentException("节点 id 不能为空");
        }

        try (Session session = driver.session()) {
            return session.run("""
                            MATCH (n)
                            WHERE n.vid = $nodeId OR elementId(n) = $nodeId
                            RETURN elementId(n) AS elementId, labels(n) AS labels, properties(n) AS properties
                            LIMIT 1
                            """,
                    parameters("nodeId", nodeId)
            ).stream()
                    .findFirst()
                    .map(this::mapNodeRecord)
                    .orElseThrow(() -> new IllegalArgumentException("节点不存在：" + nodeId));
        }
    }

    public GraphEdge getRelationship(String relationshipId) {
        if (!StringUtils.hasText(relationshipId)) {
            throw new IllegalArgumentException("关系 id 不能为空");
        }

        try (Session session = driver.session()) {
            return session.run("""
                            MATCH (a)-[r]->(b)
                            WHERE elementId(r) = $relationshipId
                            RETURN elementId(r) AS id,
                                   type(r) AS type,
                                   coalesce(a.vid, elementId(a)) AS source,
                                   coalesce(b.vid, elementId(b)) AS target,
                                   properties(r) AS properties
                            LIMIT 1
                            """,
                    parameters("relationshipId", relationshipId)
            ).stream()
                    .findFirst()
                    .map(this::mapEdgeRecord)
                    .orElseThrow(() -> new IllegalArgumentException("关系不存在：" + relationshipId));
        }
    }

    public GraphData visualize(String keyword, int depth, int limit) {
        String safeKeyword = cleanKeyword(keyword);
        List<String> searchTerms = buildSearchTerms(safeKeyword);
        int safeDepth = safeDepth(depth);
        int safeLimit = safeLimit(limit);

        try (Session session = driver.session()) {
            if (!StringUtils.hasText(safeKeyword)) {
                return queryDefaultGraph(session, safeLimit);
            }
            return queryKeywordGraph(session, searchTerms, safeDepth, safeLimit);
        }
    }

    public String buildGraphContext(String keyword, int depth, int limit) {
        GraphSearchResult searchResult = search(keyword, Math.min(limit, 20));
        GraphData graphData = visualize(keyword, depth, Math.min(limit, 50));

        StringBuilder sb = new StringBuilder();
        sb.append("GRAPH_RESULT\n");
        sb.append("检索关键词：").append(cleanKeyword(keyword)).append("\n");
        sb.append("命中的实体数量：").append(searchResult.nodeCount()).append("\n");
        sb.append("命中的关系数量：").append(searchResult.relationshipCount()).append("\n\n");

        sb.append("相关实体：\n");
        for (GraphNode node : graphData.nodes()) {
            sb.append("- ")
                    .append(node.label())
                    .append("，类型：")
                    .append(node.type())
                    .append("，vid：")
                    .append(node.id())
                    .append("，属性：")
                    .append(node.properties())
                    .append("\n");
        }

        sb.append("\n相关关系：\n");
        for (GraphEdge edge : graphData.edges()) {
            sb.append("- ")
                    .append(edge.source())
                    .append(" -[")
                    .append(edge.type())
                    .append("]-> ")
                    .append(edge.target())
                    .append("，属性：")
                    .append(edge.properties())
                    .append("\n");
        }

        if (graphData.nodes().isEmpty() && graphData.edges().isEmpty()) {
            sb.append("当前知识图谱未检索到相关实体或关系。\n");
        }
        return sb.toString();
    }

    private GraphData queryDefaultGraph(Session session, int limit) {
        Result result = session.run("""
                MATCH p=(a)-[r]->(b)
                WITH p, rand() AS randomOrder
                ORDER BY randomOrder
                RETURN p
                LIMIT %d
                """.formatted(limit));
        return trimGraphData(mapPaths(result), limit);
    }

    private GraphData queryKeywordGraph(Session session, List<String> searchTerms, int depth, int limit) {
        String cypher = """
                MATCH (start)
                WHERE any(term IN $terms WHERE term <> '' AND (
                    any(label IN labels(start) WHERE label CONTAINS term)
                    OR any(key IN keys(start) WHERE toString(start[key]) CONTAINS term)
                ))
                WITH start
                LIMIT 20
                MATCH p=(start)-[*1..%d]-(neighbor)
                RETURN p
                LIMIT %d
                """.formatted(depth, limit);

        Result result = session.run(cypher, parameters("terms", searchTerms));
        return trimGraphData(mapPaths(result), limit);
    }

    private long countSearchNodes(Session session, String keyword, List<String> searchTerms) {
        return session.run("""
                        MATCH (n)
                        WHERE $keyword = ''
                           OR any(term IN $terms WHERE term <> '' AND (
                               any(label IN labels(n) WHERE label CONTAINS term)
                               OR any(key IN keys(n) WHERE toString(n[key]) CONTAINS term)
                           ))
                        RETURN count(n) AS total
                        """,
                parameters("keyword", keyword, "terms", searchTerms)
        ).single().get("total").asLong();
    }

    private long countSearchRelationships(Session session, String keyword, List<String> searchTerms) {
        return session.run("""
                        MATCH (a)-[r]->(b)
                        WHERE $keyword = ''
                           OR any(term IN $terms WHERE term <> '' AND (
                               type(r) CONTAINS term
                               OR any(key IN keys(r) WHERE toString(r[key]) CONTAINS term)
                               OR any(key IN keys(a) WHERE toString(a[key]) CONTAINS term)
                               OR any(key IN keys(b) WHERE toString(b[key]) CONTAINS term)
                           ))
                        RETURN count(r) AS total
                        """,
                parameters("keyword", keyword, "terms", searchTerms)
        ).single().get("total").asLong();
    }

    private List<GraphNode> querySearchNodes(Session session, String keyword, List<String> searchTerms, int limit) {
        return session.run("""
                        MATCH (n)
                        WHERE $keyword = ''
                           OR any(term IN $terms WHERE term <> '' AND (
                               any(label IN labels(n) WHERE label CONTAINS term)
                               OR any(key IN keys(n) WHERE toString(n[key]) CONTAINS term)
                           ))
                        RETURN elementId(n) AS elementId, labels(n) AS labels, properties(n) AS properties
                        LIMIT %d
                        """.formatted(limit),
                parameters("keyword", keyword, "terms", searchTerms)
        ).list(this::mapNodeRecord);
    }

    private List<GraphEdge> querySearchRelationships(Session session, String keyword, List<String> searchTerms, int limit) {
        return session.run("""
                        MATCH (a)-[r]->(b)
                        WHERE $keyword = ''
                           OR any(term IN $terms WHERE term <> '' AND (
                               type(r) CONTAINS term
                               OR any(key IN keys(r) WHERE toString(r[key]) CONTAINS term)
                               OR any(key IN keys(a) WHERE toString(a[key]) CONTAINS term)
                               OR any(key IN keys(b) WHERE toString(b[key]) CONTAINS term)
                           ))
                        RETURN elementId(r) AS id,
                               type(r) AS type,
                               coalesce(a.vid, elementId(a)) AS source,
                               coalesce(b.vid, elementId(b)) AS target,
                               properties(r) AS properties
                        LIMIT %d
                        """.formatted(limit),
                parameters("keyword", keyword, "terms", searchTerms)
        ).list(this::mapEdgeRecord);
    }

    private GraphData mapPaths(Result result) {
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        Map<String, GraphEdge> edges = new LinkedHashMap<>();
        Map<String, String> elementIdToNodeId = new LinkedHashMap<>();

        while (result.hasNext()) {
            Path path = result.next().get("p").asPath();

            for (Node node : path.nodes()) {
                GraphNode graphNode = mapNode(node);
                nodes.putIfAbsent(graphNode.id(), graphNode);
                elementIdToNodeId.put(node.elementId(), graphNode.id());
            }

            for (Relationship relationship : path.relationships()) {
                GraphEdge graphEdge = mapRelationship(relationship, elementIdToNodeId);
                edges.putIfAbsent(graphEdge.id(), graphEdge);
            }
        }

        return new GraphData(new ArrayList<>(nodes.values()), new ArrayList<>(edges.values()));
    }

    private GraphData trimGraphData(GraphData graphData, int maxNodes) {
        if (graphData.nodes().size() <= maxNodes) {
            return graphData;
        }

        List<GraphNode> nodes = graphData.nodes().stream()
                .limit(maxNodes)
                .toList();
        LinkedHashSet<String> nodeIds = nodes.stream()
                .map(GraphNode::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<GraphEdge> edges = graphData.edges().stream()
                .filter(edge -> nodeIds.contains(edge.source()) && nodeIds.contains(edge.target()))
                .toList();

        return new GraphData(nodes, edges);
    }

    private GraphNode mapNodeRecord(Record record) {
        String elementId = record.get("elementId").asString();
        List<String> labels = record.get("labels").asList(Value::asString);
        Map<String, Object> properties = orderedMap(record.get("properties").asMap());
        return buildGraphNode(elementId, labels, properties);
    }

    private GraphEdge mapEdgeRecord(Record record) {
        return new GraphEdge(
                record.get("id").asString(),
                record.get("source").asString(),
                record.get("target").asString(),
                record.get("type").asString(),
                orderedMap(record.get("properties").asMap())
        );
    }

    private GraphNode mapNode(Node node) {
        List<String> labels = new ArrayList<>();
        node.labels().forEach(labels::add);
        return buildGraphNode(node.elementId(), labels, orderedMap(node.asMap()));
    }

    private GraphEdge mapRelationship(Relationship relationship, Map<String, String> elementIdToNodeId) {
        String source = elementIdToNodeId.getOrDefault(relationship.startNodeElementId(), relationship.startNodeElementId());
        String target = elementIdToNodeId.getOrDefault(relationship.endNodeElementId(), relationship.endNodeElementId());
        return new GraphEdge(
                relationship.elementId(),
                source,
                target,
                relationship.type(),
                orderedMap(relationship.asMap())
        );
    }

    private GraphNode buildGraphNode(String elementId, List<String> labels, Map<String, Object> properties) {
        String id = firstNonBlank(properties.get("vid"), elementId);
        String label = firstNonBlank(
                properties.get("vid"),
                properties.get("name"),
                properties.get("名称"),
                properties.get("label"),
                properties.get("标题"),
                elementId
        );
        String type = labels.stream()
                .filter(labelName -> !"Entity".equals(labelName))
                .filter(labelName -> !"MissingEndpoint".equals(labelName))
                .findFirst()
                .orElse(labels.isEmpty() ? "Entity" : labels.get(0));

        return new GraphNode(id, elementId, label, type, labels, properties);
    }

    private long singleLong(Session session, String cypher) {
        return session.run(cypher).single().get("total").asLong();
    }

    private int safeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int safeDepth(int depth) {
        if (depth <= 0) {
            return DEFAULT_DEPTH;
        }
        return Math.min(depth, MAX_DEPTH);
    }

    private String cleanKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private List<String> buildSearchTerms(String keyword) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String cleaned = cleanKeyword(keyword);
        if (!StringUtils.hasText(cleaned)) {
            return List.of();
        }

        addSearchTerm(terms, cleaned);

        String normalized = cleaned
                .replace("？", " ")
                .replace("?", " ")
                .replace("，", " ")
                .replace(",", " ")
                .replace("。", " ")
                .replace("；", " ")
                .replace(";", " ")
                .replace("：", " ")
                .replace(":", " ")
                .replace("、", " ")
                .replace("的信息", " ")
                .replace("的情况", " ")
                .replace("情况", " ")
                .replace("信息", " ")
                .replace("有哪些", " ")
                .replace("有多少", " ")
                .replace("多少", " ")
                .replace("是什么", " ")
                .replace("是谁", " ");

        for (String part : normalized.split("\\s+|的")) {
            addSearchTerm(terms, part);
        }

        return terms.stream()
                .filter(term -> term.length() >= 2)
                .limit(12)
                .toList();
    }

    private void addSearchTerm(LinkedHashSet<String> terms, String value) {
        String term = cleanKeyword(value);
        if (!StringUtils.hasText(term)) {
            return;
        }

        terms.add(term);

        int chineseParenIndex = term.indexOf('（');
        if (chineseParenIndex > 0) {
            terms.add(term.substring(0, chineseParenIndex).trim());
        }

        int englishParenIndex = term.indexOf('(');
        if (englishParenIndex > 0) {
            terms.add(term.substring(0, englishParenIndex).trim());
        }
    }

    private Map<String, Object> orderedMap(Map<String, Object> source) {
        return new LinkedHashMap<>(source);
    }

    private String firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = Objects.toString(value, "").trim();
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }
}
