package com.hgx.hgxaiagent.knowledgegraph.model;

import java.util.List;

public record GraphData(
        List<GraphNode> nodes,
        List<GraphEdge> edges
) {
}
