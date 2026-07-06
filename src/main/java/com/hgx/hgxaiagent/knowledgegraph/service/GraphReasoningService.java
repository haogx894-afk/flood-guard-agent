package com.hgx.hgxaiagent.knowledgegraph.service;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.neo4j.driver.Values.parameters;

/**
 * 知识图谱业务推理服务。
 * 用固定的业务路径查询隐藏关系，避免只靠关键词模糊搜索导致结果发散。
 */
@Service
public class GraphReasoningService {

    private static final int MAX_GROUP_ITEMS = 120;

    private final Driver driver;
    private final KnowledgeGraphService knowledgeGraphService;

    public GraphReasoningService(Driver driver, KnowledgeGraphService knowledgeGraphService) {
        this.driver = driver;
        this.knowledgeGraphService = knowledgeGraphService;
    }

    /**
     * 推理：区级行政区 -> 镇级行政区 -> 险村。
     */
    public String queryRiskVillagesByDistrict(String districtName) {
        String safeName = clean(districtName);
        if (!StringUtils.hasText(safeName)) {
            return "RISK_VILLAGES_BY_DISTRICT_RESULT\n请提供区级行政区名称，例如：怀柔区。";
        }

        try (Session session = driver.session()) {
            List<GroupResult> groups = session.run("""
                            MATCH (district:`区级行政区`)
                            WHERE any(key IN keys(district) WHERE toString(district[key]) CONTAINS $name)
                            MATCH path=(v:`险村`)-[:`属于`|`位于`*1..3]->(district)
                            WITH DISTINCT v, [node IN nodes(path) WHERE '镇级行政区' IN labels(node)][0] AS town
                            WITH
                                CASE
                                    WHEN town IS NULL THEN '未识别乡镇'
                                    ELSE coalesce(town.name, town.`名称`, town.label, town.vid, elementId(town))
                                END AS groupName,
                                v,
                                coalesce(v.name, v.`名称`, v.label, v.vid, elementId(v)) AS itemName
                            ORDER BY groupName, itemName
                            RETURN groupName, collect(DISTINCT itemName) AS items, count(DISTINCT v) AS count
                            ORDER BY groupName
                            """,
                    parameters("name", safeName)
            ).list(this::mapGroupResult);

            return formatGroupedResult(
                    "RISK_VILLAGES_BY_DISTRICT_RESULT",
                    "查询行政区：" + safeName,
                    "推理路径：险村 -[属于/位于*1..3]-> 区级行政区，并从路径中识别镇级行政区。",
                    "险村",
                    groups
            );
        }
    }

    /**
     * 推理：镇级行政区 -> 险村。
     */
    public String queryRiskVillagesByTown(String townName) {
        String safeName = clean(townName);
        if (!StringUtils.hasText(safeName)) {
            return "RISK_VILLAGES_BY_TOWN_RESULT\n请提供镇级行政区名称，例如：九渡河镇。";
        }

        try (Session session = driver.session()) {
            List<GroupResult> groups = session.run("""
                            MATCH (town:`镇级行政区`)
                            WHERE any(key IN keys(town) WHERE toString(town[key]) CONTAINS $name)
                            MATCH path=(v:`险村`)-[:`属于`|`位于`*1..2]->(town)
                            OPTIONAL MATCH (town)-[:`属于`|`位于`*1..2]->(district:`区级行政区`)
                            WITH
                                coalesce(district.name, district.`名称`, district.label, district.vid, '未识别区县') AS districtName,
                                coalesce(town.name, town.`名称`, town.label, town.vid, elementId(town)) AS townName,
                                v,
                                coalesce(v.name, v.`名称`, v.label, v.vid, elementId(v)) AS itemName
                            ORDER BY districtName, townName, itemName
                            RETURN districtName + ' / ' + townName AS groupName,
                                   collect(DISTINCT itemName) AS items,
                                   count(DISTINCT v) AS count
                            ORDER BY groupName
                            """,
                    parameters("name", safeName)
            ).list(this::mapGroupResult);

            return formatGroupedResult(
                    "RISK_VILLAGES_BY_TOWN_RESULT",
                    "查询乡镇：" + safeName,
                    "推理路径：险村 -[属于/位于*1..2]-> 镇级行政区。",
                    "险村",
                    groups
            );
        }
    }

    /**
     * 推理：山洪沟流域包含的重点对象。
     */
    public String queryObjectsByBasin(String basinName) {
        String safeName = clean(basinName);
        if (!StringUtils.hasText(safeName)) {
            return "OBJECTS_BY_BASIN_RESULT\n请提供山洪沟流域名称。";
        }

        try (Session session = driver.session()) {
            List<GroupResult> groups = session.run("""
                            MATCH (basin:`山洪沟流域`)
                            WHERE any(key IN keys(basin) WHERE toString(basin[key]) CONTAINS $name)
                            CALL {
                                WITH basin
                                MATCH (obj)-[r]->(basin)
                                WHERE type(r) IN ['位于', '属于', '关联', '监测', '流向']
                                RETURN obj
                                UNION
                                WITH basin
                                MATCH (basin)-[r:`包含`]->(obj)
                                RETURN obj
                            }
                            WITH
                                head([label IN labels(obj) WHERE label <> 'Entity' | label]) AS groupName,
                                obj,
                                coalesce(obj.name, obj.`名称`, obj.label, obj.vid, elementId(obj)) AS itemName
                            WHERE groupName IS NOT NULL
                            ORDER BY groupName, itemName
                            RETURN groupName, collect(DISTINCT itemName) AS items, count(DISTINCT obj) AS count
                            ORDER BY count DESC, groupName
                            """,
                    parameters("name", safeName)
            ).list(this::mapGroupResult);

            return formatGroupedResult(
                    "OBJECTS_BY_BASIN_RESULT",
                    "查询山洪沟流域：" + safeName,
                    "推理路径：对象 -[位于/属于]-> 山洪沟流域，或 山洪沟流域 -[包含]-> 对象。",
                    "对象",
                    groups
            );
        }
    }

    /**
     * 推理：流域内监测站及其监测对象。
     */
    public String queryMonitoringByBasin(String basinName) {
        String safeName = clean(basinName);
        if (!StringUtils.hasText(safeName)) {
            return "MONITORING_BY_BASIN_RESULT\n请提供山洪沟流域名称。";
        }

        try (Session session = driver.session()) {
            List<GroupResult> groups = session.run("""
                            MATCH (basin:`山洪沟流域`)
                            WHERE any(key IN keys(basin) WHERE toString(basin[key]) CONTAINS $name)
                            CALL {
                                WITH basin
                                MATCH (station)-[:`位于`]->(basin)
                                RETURN station
                                UNION
                                WITH basin
                                MATCH (basin)-[:`包含`]->(station)
                                RETURN station
                            }
                            WITH DISTINCT station
                            WHERE any(label IN labels(station) WHERE label IN [
                                '雨量站', '墒情站', '视频站', '水库报讯站', '水库实时站',
                                '河道报讯站', '河道实时站', '地埋水位计'
                            ])
                            OPTIONAL MATCH (station)-[:`监测`|`关联`]->(target)
                            WITH
                                head([label IN labels(station) WHERE label <> 'Entity' | label]) AS groupName,
                                coalesce(station.name, station.`名称`, station.label, station.vid, elementId(station)) AS stationName,
                                collect(DISTINCT CASE
                                    WHEN target IS NULL THEN NULL
                                    ELSE coalesce(target.name, target.`名称`, target.label, target.vid, elementId(target))
                                END) AS targets
                            WITH groupName, stationName, [target IN targets WHERE target IS NOT NULL][0..5] AS targetNames
                            WITH groupName,
                                 stationName + CASE
                                     WHEN size(targetNames) = 0 THEN ''
                                     ELSE ' -> ' + reduce(text = '', target IN targetNames |
                                         text + CASE WHEN text = '' THEN target ELSE '、' + target END
                                     )
                                 END AS itemName
                            ORDER BY groupName, itemName
                            RETURN groupName, collect(DISTINCT itemName) AS items, count(DISTINCT itemName) AS count
                            ORDER BY count DESC, groupName
                            """,
                    parameters("name", safeName)
            ).list(this::mapGroupResult);

            return formatGroupedResult(
                    "MONITORING_BY_BASIN_RESULT",
                    "查询山洪沟流域：" + safeName,
                    "推理路径：监测站 -[位于]-> 山洪沟流域 / 山洪沟流域 -[包含]-> 监测站，并继续查 监测站 -[监测/关联]-> 目标对象。",
                    "监测站",
                    groups
            );
        }
    }

    /**
     * 推理：危险区影响对象。
     */
    public String queryObjectsByDangerZone(String dangerZoneName) {
        String safeName = clean(dangerZoneName);
        if (!StringUtils.hasText(safeName)) {
            return "OBJECTS_BY_DANGER_ZONE_RESULT\n请提供危险区名称。";
        }

        try (Session session = driver.session()) {
            List<GroupResult> groups = session.run("""
                            MATCH (danger:`危险区`)
                            WHERE any(key IN keys(danger) WHERE toString(danger[key]) CONTAINS $name)
                            CALL {
                                WITH danger
                                MATCH (obj)-[:`位于`]->(danger)
                                RETURN obj
                                UNION
                                WITH danger
                                MATCH (danger)-[:`包含`]->(obj)
                                RETURN obj
                            }
                            WITH
                                head([label IN labels(obj) WHERE label <> 'Entity' | label]) AS groupName,
                                obj,
                                coalesce(obj.name, obj.`名称`, obj.label, obj.vid, elementId(obj)) AS itemName
                            WHERE groupName IS NOT NULL
                            ORDER BY groupName, itemName
                            RETURN groupName, collect(DISTINCT itemName) AS items, count(DISTINCT obj) AS count
                            ORDER BY count DESC, groupName
                            """,
                    parameters("name", safeName)
            ).list(this::mapGroupResult);

            return formatGroupedResult(
                    "OBJECTS_BY_DANGER_ZONE_RESULT",
                    "查询危险区：" + safeName,
                    "推理路径：对象 -[位于]-> 危险区，或 危险区 -[包含]-> 对象。",
                    "对象",
                    groups
            );
        }
    }

    /**
     * 推理：险村转移路线与安置点。
     */
    public String queryEvacuationByVillage(String villageName) {
        String safeName = clean(villageName);
        if (!StringUtils.hasText(safeName)) {
            return "EVACUATION_BY_VILLAGE_RESULT\n请提供险村名称。";
        }

        try (Session session = driver.session()) {
            List<Record> records = session.run("""
                            MATCH (v:`险村`)
                            WHERE any(key IN keys(v) WHERE toString(v[key]) CONTAINS $name)
                            OPTIONAL MATCH (route:`转移路线`)-[:`关联`]->(v)
                            OPTIONAL MATCH (route)-[:`到达`]->(routeShelter:`安置点`)
                            OPTIONAL MATCH (directShelter:`安置点`)-[:`属于`]->(v)
                            WITH
                                coalesce(v.name, v.`名称`, v.label, v.vid, elementId(v)) AS villageName,
                                collect(DISTINCT CASE
                                    WHEN route IS NULL THEN NULL
                                    ELSE coalesce(route.name, route.`名称`, route.label, route.vid, elementId(route))
                                END) AS routes,
                                collect(DISTINCT CASE
                                    WHEN routeShelter IS NULL THEN NULL
                                    ELSE coalesce(routeShelter.name, routeShelter.`名称`, routeShelter.label, routeShelter.vid, elementId(routeShelter))
                                END) +
                                collect(DISTINCT CASE
                                    WHEN directShelter IS NULL THEN NULL
                                    ELSE coalesce(directShelter.name, directShelter.`名称`, directShelter.label, directShelter.vid, elementId(directShelter))
                                END) AS shelters
                            RETURN villageName, routes, shelters
                            ORDER BY villageName
                            LIMIT 20
                            """,
                    parameters("name", safeName)
            ).list();

            if (records.isEmpty()) {
                return "EVACUATION_BY_VILLAGE_RESULT\n当前知识图谱未查询到与“" + safeName + "”匹配的险村转移信息。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("EVACUATION_BY_VILLAGE_RESULT\n");
            sb.append("查询险村：").append(safeName).append("\n");
            sb.append("推理路径：转移路线 -[关联]-> 险村；转移路线 -[到达]-> 安置点；安置点 -[属于]-> 险村。\n\n");
            for (Record record : records) {
                sb.append("【").append(record.get("villageName").asString()).append("】\n");
                sb.append("转移路线：").append(formatValueList(asStringList(record.get("routes")))).append("\n");
                sb.append("安置点：").append(formatValueList(asStringList(record.get("shelters")))).append("\n\n");
            }
            return sb.toString();
        }
    }

    /**
     * 推理：上游/下游影响链。
     */
    public String queryUpDownstreamImpact(String objectName) {
        String safeName = clean(objectName);
        if (!StringUtils.hasText(safeName)) {
            return "UP_DOWNSTREAM_IMPACT_RESULT\n请提供河段或险村名称。";
        }

        try (Session session = driver.session()) {
            List<String> lines = session.run("""
                            MATCH (start)
                            WHERE any(key IN keys(start) WHERE toString(start[key]) CONTAINS $name)
                            WITH start LIMIT 10
                            CALL {
                                WITH start
                                MATCH (start)-[r:`上游`|`下游`]->(target)
                                RETURN
                                    coalesce(start.name, start.`名称`, start.label, start.vid, elementId(start)) AS source,
                                    type(r) AS relation,
                                    coalesce(target.name, target.`名称`, target.label, target.vid, elementId(target)) AS target
                                UNION
                                WITH start
                                MATCH (source)-[r:`上游`|`下游`]->(start)
                                RETURN
                                    coalesce(source.name, source.`名称`, source.label, source.vid, elementId(source)) AS source,
                                    type(r) AS relation,
                                    coalesce(start.name, start.`名称`, start.label, start.vid, elementId(start)) AS target
                            }
                            RETURN DISTINCT source, relation, target
                            LIMIT 100
                            """,
                    parameters("name", safeName)
            ).list(record -> "- " + record.get("source").asString()
                    + " -[" + record.get("relation").asString() + "]-> "
                    + record.get("target").asString());

            if (lines.isEmpty()) {
                return "UP_DOWNSTREAM_IMPACT_RESULT\n当前知识图谱未查询到“" + safeName + "”的上游/下游关系。";
            }

            return """
                    UP_DOWNSTREAM_IMPACT_RESULT
                    查询对象：%s
                    推理路径：对象 -[上游/下游]- 对象。以下保留原始边方向，回答时需要说明“依据图谱边方向”。

                    %s
                    """.formatted(safeName, String.join("\n", lines));
        }
    }

    /**
     * 通用实体上下文，作为专用规则无法覆盖时的兜底。
     */
    public String queryEntityFullContext(String entityName) {
        return knowledgeGraphService.buildGraphContext(entityName, 4, 100);
    }

    public String getReasoningRules() {
        return """
                GRAPH_REASONING_RULES
                1. 行政归属：险村 -> 村级行政区 -> 镇级行政区 -> 区级行政区，可推理“某区/某镇包含哪些险村”。
                2. 流域归属：对象 -> 山洪沟流域，或 山洪沟流域 -> 包含 -> 对象，可推理“某流域内有哪些对象”。
                3. 监测覆盖：监测站 -> 山洪沟流域 / 河段 / 险村 / 监测断面，可推理“某流域、某河段、某险村被哪些站点覆盖”。
                4. 危险区影响：对象 -> 危险区，或 危险区 -> 包含 -> 对象，可推理“危险区影响哪些村、景区、露营地、企事业单位”。
                5. 转移安置：转移路线 -> 险村；转移路线 -> 安置点；安置点 -> 险村，可推理“某险村有哪些路线和安置点”。
                6. 上下游影响：河段/险村 -> 上游/下游 -> 河段/险村，可推理上下游影响链。
                默认最大推理深度建议为 4；超过 4 层容易引入弱相关对象。
                """;
    }

    private GroupResult mapGroupResult(Record record) {
        return new GroupResult(
                record.get("groupName").asString(),
                record.get("count").asLong(),
                asStringList(record.get("items"))
        );
    }

    private List<String> asStringList(Value value) {
        return value.asList(item -> item.isNull() ? "" : item.asString());
    }

    private String formatGroupedResult(String title, String queryLine, String pathLine, String itemType, List<GroupResult> groups) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n");
        sb.append(queryLine).append("\n");
        sb.append(pathLine).append("\n");

        if (groups.isEmpty()) {
            sb.append("当前知识图谱未查询到匹配结果。\n");
            return sb.toString();
        }

        long total = groups.stream().mapToLong(GroupResult::count).sum();
        sb.append(itemType).append("总数：").append(total).append("\n");
        sb.append("分组数量：").append(groups.size()).append("\n\n");

        for (GroupResult group : groups) {
            sb.append("【").append(group.groupName()).append("】")
                    .append(group.count()).append("个\n");
            sb.append(formatValueList(group.items())).append("\n\n");
        }

        return sb.toString();
    }

    private String formatValueList(List<String> values) {
        List<String> cleanedValues = values.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .limit(MAX_GROUP_ITEMS)
                .collect(Collectors.toCollection(ArrayList::new));
        if (cleanedValues.isEmpty()) {
            return "当前图谱未记录";
        }
        String suffix = values.stream().filter(StringUtils::hasText).distinct().count() > MAX_GROUP_ITEMS
                ? "（仅展示前 " + MAX_GROUP_ITEMS + " 个）"
                : "";
        return String.join("、", cleanedValues) + suffix;
    }

    private String clean(String value) {
        String text = Objects.toString(value, "").trim();
        String[] noiseWords = {
                "？", "?", "。", "，", ",",
                "有哪些险村", "有多少险村", "险村有多少",
                "有哪些对象", "有哪些监测站", "监测覆盖情况",
                "有哪些安置点", "有哪些转移路线", "怎么转移", "如何转移",
                "有哪些", "有多少", "多少",
                "的信息", "的情况", "信息", "情况"
        };
        for (String noiseWord : noiseWords) {
            text = text.replace(noiseWord, " ");
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private record GroupResult(String groupName, long count, List<String> items) {
    }
}
