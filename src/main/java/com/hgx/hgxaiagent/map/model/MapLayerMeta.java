package com.hgx.hgxaiagent.map.model;

public record MapLayerMeta(
        String id,
        String name,
        String geometry,
        String color,
        int order,
        long featureCount
) {
}
