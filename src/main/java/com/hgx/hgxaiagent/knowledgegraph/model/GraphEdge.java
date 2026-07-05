package com.hgx.hgxaiagent.knowledgegraph.model;

import java.util.Map;

public record GraphEdge(
        String id,
        String source,
        String target,
        String type,
        Map<String, Object> properties
) {
}
