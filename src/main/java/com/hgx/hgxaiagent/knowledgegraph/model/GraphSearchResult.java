package com.hgx.hgxaiagent.knowledgegraph.model;

import java.util.List;

public record GraphSearchResult(
        String keyword,
        long nodeCount,
        long relationshipCount,
        List<GraphNode> nodes,
        List<GraphEdge> edges
) {
}
