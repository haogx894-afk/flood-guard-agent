# -*- coding: utf-8 -*-
"""
山洪防御地图示例数据生成脚本

生成 6 个图层的 GeoJSON 演示数据（流域 / 山洪沟道 / 河段 / 断面 / 危险区 / 村庄 / 监测站），
供 Spring Boot 后端 map 模块加载，前端 Leaflet 地图展示。

要点：
1. 实体类型与知识图谱本体一致（流域—沟道—河段—断面—危险区—村庄），
   properties 中的 vid / name 可用于与 Neo4j 图谱节点联动检索。
2. 内置 WGS84 -> GCJ-02 坐标纠偏：原始数据按 WGS84 组织，
   输出时转换为 GCJ-02（火星坐标系），与高德 / 国内互联网瓦片底图对齐。
3. 输出 manifest.json 记录数据版本、图层清单与整体范围，支撑"版本管理"。

注意：本数据为演示用示意数据，空间位置仅参考北京房山—门头沟西部山区大致形态，
不对应真实地理实体，禁止用于实际防灾业务。

用法：
    python scripts/generate_map_data.py
输出：
    src/main/resources/mapdata/v<版本号>/*.geojson + manifest.json
"""

import json
import math
import random
from datetime import datetime, timezone, timedelta
from pathlib import Path

# ---------------------------------------------------------------- 配置

DATA_VERSION = "1.0.0"
OUTPUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "mapdata" / f"v{DATA_VERSION}"

RNG = random.Random(20260121)

# 演示区域：北京西部山区（房山—门头沟一带）大致范围，WGS84
REGION_MIN_LON, REGION_MAX_LON = 115.55, 115.95
REGION_MIN_LAT, REGION_MAX_LAT = 39.60, 39.85

# ------------------------------------------------------- WGS84 -> GCJ-02
# 经典 eviltransform 算法，国内互联网地图（高德/腾讯）底图使用 GCJ-02。

_A = 6378245.0          # 克拉索夫斯基椭球长半轴
_EE = 0.00669342162296594323  # 偏心率平方


def _transform_lat(x, y):
    ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * math.sqrt(abs(x))
    ret += (20.0 * math.sin(6.0 * x * math.pi) + 20.0 * math.sin(2.0 * x * math.pi)) * 2.0 / 3.0
    ret += (20.0 * math.sin(y * math.pi) + 40.0 * math.sin(y / 3.0 * math.pi)) * 2.0 / 3.0
    ret += (160.0 * math.sin(y / 12.0 * math.pi) + 320 * math.sin(y * math.pi / 30.0)) * 2.0 / 3.0
    return ret


def _transform_lon(x, y):
    ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * math.sqrt(abs(x))
    ret += (20.0 * math.sin(6.0 * x * math.pi) + 20.0 * math.sin(2.0 * x * math.pi)) * 2.0 / 3.0
    ret += (20.0 * math.sin(x * math.pi) + 40.0 * math.sin(x / 3.0 * math.pi)) * 2.0 / 3.0
    ret += (150.0 * math.sin(x / 12.0 * math.pi) + 300.0 * math.sin(x / 30.0 * math.pi)) * 2.0 / 3.0
    return ret


def wgs84_to_gcj02(lon, lat):
    """WGS84 经纬度转 GCJ-02（中国大陆范围内）。"""
    dlat = _transform_lat(lon - 105.0, lat - 35.0)
    dlon = _transform_lon(lon - 105.0, lat - 35.0)
    radlat = lat / 180.0 * math.pi
    magic = math.sin(radlat)
    magic = 1 - _EE * magic * magic
    sqrtmagic = math.sqrt(magic)
    dlat = (dlat * 180.0) / ((_A * (1 - _EE)) / (magic * sqrtmagic) * math.pi)
    dlon = (dlon * 180.0) / (_A / sqrtmagic * math.cos(radlat) * math.pi)
    return lon + dlon, lat + dlat


def convert_coords(coords):
    """递归转换 GeoJSON 坐标数组（支持 Point/LineString/Polygon 任意嵌套深度）。"""
    if isinstance(coords[0], (int, float)):
        lon, lat = wgs84_to_gcj02(coords[0], coords[1])
        return [round(lon, 6), round(lat, 6)]
    return [convert_coords(c) for c in coords]


# ---------------------------------------------------------------- 数据构造

BASIN_NAME = "大石河流域"

GULLY_NAMES = ["周口店沟", "黄山店沟", "南窖沟", "大安山沟", "史家营沟", "霞云岭沟", "蒲洼沟"]

VILLAGE_NAMES = ["周口店村", "黄山店村", "南窖村", "中窖村", "大安山村", "史家营村",
                 "莲花庵村", "霞云岭村", "堂上村", "蒲洼村", "东村村", "宝水村"]

STATION_NAMES = ["周口店雨量站", "南窖雨量站", "大安山水位站", "史家营雨量站", "霞云岭水位站"]


def irregular_polygon(cx, cy, rx, ry, n=14, jitter=0.35):
    """以 (cx, cy) 为中心生成不规则多边形（度为单位）。"""
    angles = sorted(RNG.uniform(0, 2 * math.pi) for _ in range(n))
    ring = []
    for a in angles:
        r = 1.0 - jitter / 2 + RNG.uniform(0, jitter)
        ring.append([cx + math.cos(a) * rx * r, cy + math.sin(a) * ry * r])
    ring.append(ring[0])  # 闭合
    return [ring]


def downhill_line(start_lon, start_lat, length_deg, bearing_deg, wiggle=0.25, steps=10):
    """从起点沿某方向生成一条"顺山势下行"的折线（山区 -> 平原，大体向东/东南）。"""
    pts = [[start_lon, start_lat]]
    lon, lat = start_lon, start_lat
    for _ in range(steps):
        seg = length_deg / steps
        b = math.radians(bearing_deg + RNG.uniform(-wiggle * 100, wiggle * 100))
        lon += seg * math.cos(b)
        lat += seg * math.sin(b) * 0.8  # 纬向略压缩，模拟河谷形态
        pts.append([lon, lat])
    return pts


def make_feature(vid, name, ftype, geom_type, coords_wgs84, props):
    geom = {"type": geom_type, "coordinates": convert_coords(coords_wgs84)}
    properties = {"vid": vid, "name": name, "entityType": ftype}
    properties.update(props)
    return {"type": "Feature", "geometry": geom, "properties": properties}


def build_layers():
    layers = {}

    # 1. 流域（面）
    basin_cx = (REGION_MIN_LON + REGION_MAX_LON) / 2
    basin_cy = (REGION_MIN_LAT + REGION_MAX_LAT) / 2
    layers["basin"] = [make_feature(
        "basin-001", BASIN_NAME, "流域", "Polygon",
        irregular_polygon(basin_cx, basin_cy, 0.19, 0.115),
        {"code": "FS-DSH-001", "areaKm2": 128.5, "level": "县级", "description": "演示流域（示意数据）"},
    )]

    # 2. 山洪沟道（线）+ 3. 河段（线）+ 4. 断面（点）
    gullies, reaches, sections = [], [], []
    for i, gname in enumerate(GULLY_NAMES):
        gid = f"gully-{i + 1:03d}"
        # 沟道起点散布在区域西北侧山区
        sx = REGION_MIN_LON + RNG.uniform(0.02, 0.18)
        sy = REGION_MIN_LAT + 0.06 + i * 0.028 + RNG.uniform(-0.008, 0.008)
        bearing = RNG.uniform(55, 105)  # 大体向东、东南下行
        length = RNG.uniform(0.16, 0.26)
        line = downhill_line(sx, sy, length, bearing)

        gullies.append(make_feature(
            gid, gname, "山洪沟道", "LineString", line,
            {"code": f"G-{i + 1:03d}", "lengthKm": round(length * 85, 1),
             "level": RNG.choice(["重点", "一般"]), "basin": BASIN_NAME},
        ))

        # 每条沟道切 2~3 个河段（对折线做分段）
        n_reach = RNG.choice([2, 3])
        seg = len(line) // n_reach
        for r in range(n_reach):
            sub = line[r * seg: (r + 1) * seg + 1] if r < n_reach - 1 else line[r * seg:]
            if len(sub) < 2:
                sub = line[-2:]
            rid = f"reach-{i + 1:03d}-{r + 1:02d}"
            reaches.append(make_feature(
                rid, f"{gname}{'上中下'[r] if n_reach == 3 else '上下'[r]}游河段", "河段",
                "LineString", sub,
                {"code": f"R-{i + 1:03d}-{r + 1:02d}", "gully": gname, "order": r + 1},
            ))
            # 河段末端布设一个断面
            ex, ey = sub[-1]
            sid = f"section-{i + 1:03d}-{r + 1:02d}"
            sections.append(make_feature(
                sid, f"{gname}{r + 1}号断面", "断面", "Point", [ex, ey],
                {"code": f"S-{i + 1:03d}-{r + 1:02d}", "gully": gname,
                 "type": RNG.choice(["水位断面", "流量断面"])},
            ))

    layers["gully"] = gullies
    layers["reach"] = reaches
    layers["section"] = sections

    # 5. 危险区（面）：布设在沟道下游出口附近
    danger_zones = []
    for i, g in enumerate(gullies):
        end = g["geometry"]["coordinates"][-1]  # 注意：已是 GCJ-02，转回 WGS84 再建面太绕，
        # 这里直接基于 WGS84 重算下游点：取对应沟道 WGS84 折线末端附近
        # 简化处理：在区域东侧沟口带随机布设
        cx = REGION_MIN_LON + RNG.uniform(0.22, 0.36)
        cy = REGION_MIN_LAT + 0.05 + i * 0.03 + RNG.uniform(-0.006, 0.006)
        dzid = f"dz-{i + 1:03d}"
        danger_zones.append(make_feature(
            dzid, f"{GULLY_NAMES[i]}沿线危险区", "危险区", "Polygon",
            irregular_polygon(cx, cy, 0.018, 0.010, n=9, jitter=0.5),
            {"code": f"DZ-{i + 1:03d}", "riskLevel": RNG.choice(["高", "中", "低"]),
             "population": RNG.randint(50, 800), "gully": GULLY_NAMES[i]},
        ))
    layers["danger_zone"] = danger_zones

    # 6. 村庄（点）：散布在沟道中下游
    villages = []
    for i, vname in enumerate(VILLAGE_NAMES):
        vx = REGION_MIN_LON + RNG.uniform(0.10, 0.36)
        vy = REGION_MIN_LAT + RNG.uniform(0.03, 0.22)
        vid = f"village-{i + 1:03d}"
        villages.append(make_feature(
            vid, vname, "村庄", "Point", [vx, vy],
            {"code": f"V-{i + 1:03d}", "population": RNG.randint(120, 1500),
             "town": RNG.choice(["周口店镇", "南窖乡", "大安山乡", "史家营乡", "霞云岭乡", "蒲洼乡"])},
        ))
    layers["village"] = villages

    # 7. 监测站（点）
    stations = []
    for i, sname in enumerate(STATION_NAMES):
        sx = REGION_MIN_LON + RNG.uniform(0.06, 0.30)
        sy = REGION_MIN_LAT + RNG.uniform(0.05, 0.20)
        stid = f"station-{i + 1:03d}"
        stations.append(make_feature(
            stid, sname, "监测站", "Point", [sx, sy],
            {"code": f"MS-{i + 1:03d}", "stationType": "雨量站" if "雨量" in sname else "水位站",
             "status": RNG.choice(["在线", "在线", "在线", "离线"])},
        ))
    layers["station"] = stations

    return layers


LAYER_META = {
    "basin":       {"name": "流域",   "geometry": "Polygon",    "color": "#3b82f6", "order": 1},
    "gully":       {"name": "山洪沟道", "geometry": "LineString", "color": "#f97316", "order": 2},
    "reach":       {"name": "河段",   "geometry": "LineString", "color": "#eab308", "order": 3},
    "danger_zone": {"name": "危险区",  "geometry": "Polygon",    "color": "#ef4444", "order": 4},
    "section":     {"name": "断面",   "geometry": "Point",      "color": "#8b5cf6", "order": 5},
    "village":     {"name": "村庄",   "geometry": "Point",      "color": "#22c55e", "order": 6},
    "station":     {"name": "监测站",  "geometry": "Point",      "color": "#06b6d4", "order": 7},
}


def main():
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    layers = build_layers()

    manifest_layers = []
    for layer_id, features in layers.items():
        fc = {"type": "FeatureCollection", "features": features}
        out = OUTPUT_DIR / f"{layer_id}.geojson"
        out.write_text(json.dumps(fc, ensure_ascii=False), encoding="utf-8")

        meta = dict(LAYER_META[layer_id])
        meta["id"] = layer_id
        meta["featureCount"] = len(features)
        manifest_layers.append(meta)
        print(f"  {layer_id:<12s} {len(features):>3d} 个要素 -> {out.name}")

    manifest = {
        "version": DATA_VERSION,
        "name": "山洪防御空间数据（演示）",
        "crs": "GCJ-02",
        "note": "示意数据，不对应真实地理实体；由 scripts/generate_map_data.py 生成，原始坐标 WGS84，输出前已做 GCJ-02 纠偏",
        "generatedAt": datetime.now(timezone(timedelta(hours=8))).isoformat(timespec="seconds"),
        "bounds": {  # GCJ-02 纠偏后的整体范围，供前端初始定位
            "minLon": convert_coords([REGION_MIN_LON, REGION_MIN_LAT])[0],
            "minLat": convert_coords([REGION_MIN_LON, REGION_MIN_LAT])[1],
            "maxLon": convert_coords([REGION_MAX_LON, REGION_MAX_LAT])[0],
            "maxLat": convert_coords([REGION_MAX_LON, REGION_MAX_LAT])[1],
        },
        "layers": sorted(manifest_layers, key=lambda m: m["order"]),
    }
    (OUTPUT_DIR / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    total = sum(len(f) for f in layers.values())
    print(f"\n完成：{len(layers)} 个图层，共 {total} 个要素，版本 v{DATA_VERSION}")
    print(f"输出目录：{OUTPUT_DIR}")


if __name__ == "__main__":
    main()
