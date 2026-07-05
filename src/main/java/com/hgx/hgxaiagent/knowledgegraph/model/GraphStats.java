package com.hgx.hgxaiagent.knowledgegraph.model;

import java.util.List;

public record GraphStats(
        long totalNodes,
        long totalRelationships,
        long placeholderNodes,
        List<GraphCountItem> nodeLabels,
        List<GraphCountItem> relationshipTypes
) {
}
