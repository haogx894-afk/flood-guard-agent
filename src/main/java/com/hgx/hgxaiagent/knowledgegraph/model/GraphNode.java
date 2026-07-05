package com.hgx.hgxaiagent.knowledgegraph.model;

import java.util.List;
import java.util.Map;

public record GraphNode(
        String id,
        String elementId,
        String label,
        String type,
        List<String> labels,
        Map<String, Object> properties
) {
}
