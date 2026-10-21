package com.hgx.hgxaiagent.map.model;

public record MapBounds(
        double minLon,
        double minLat,
        double maxLon,
        double maxLat
) {
    /**
     * 判断两个经纬度包围盒是否相交（含边界相接）。
     */
    public boolean intersects(MapBounds other) {
        if (other == null) {
            return true;
        }
        return this.minLon <= other.maxLon
                && this.maxLon >= other.minLon
                && this.minLat <= other.maxLat
                && this.maxLat >= other.minLat;
    }

    public static MapBounds parse(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return null;
        }
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("bbox 格式应为 minLon,minLat,maxLon,maxLat");
        }
        try {
            double minLon = Double.parseDouble(parts[0].trim());
            double minLat = Double.parseDouble(parts[1].trim());
            double maxLon = Double.parseDouble(parts[2].trim());
            double maxLat = Double.parseDouble(parts[3].trim());
            if (minLon > maxLon || minLat > maxLat) {
                throw new IllegalArgumentException("bbox 最小值不能大于最大值");
            }
            return new MapBounds(minLon, minLat, maxLon, maxLat);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bbox 参数必须是数字：" + bbox);
        }
    }
}
