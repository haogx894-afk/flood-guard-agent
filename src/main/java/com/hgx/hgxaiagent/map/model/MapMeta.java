package com.hgx.hgxaiagent.map.model;

import java.util.List;

public record MapMeta(
        String version,
        String name,
        String crs,
        String note,
        String generatedAt,
        MapBounds bounds,
        List<MapLayerMeta> layers
) {
}
