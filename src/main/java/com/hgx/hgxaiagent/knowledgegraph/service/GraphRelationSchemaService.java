package com.hgx.hgxaiagent.knowledgegraph.service;

import com.hgx.hgxaiagent.knowledgegraph.model.GraphRelationRule;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GraphRelationSchemaService {

    private static final List<GraphRelationRule> RELATION_RULES = List.of(
            rule("安置点", "属于", "塘坝_点数据"),
            rule("安置点", "属于", "塘坝_面数据"),
            rule("安置点", "属于", "险村"),
            rule("安置点", "属于", "镇级行政区"),
            rule("村级行政区", "属于", "山洪沟流域"),
            rule("村级行政区", "属于", "涉山涉水风景区"),
            rule("村级行政区", "属于", "险村"),
            rule("村级行政区", "属于", "镇级行政区"),
            rule("村级责任人", "负责", "桥梁"),
            rule("村级责任人", "负责", "塘坝_点数据"),
            rule("村级责任人", "负责", "塘坝_面数据"),
            rule("村级责任人", "负责", "险村"),
            rule("村级责任人", "负责", "镇级行政区"),
            rule("地埋式水位计", "监测", "监测断面"),
            rule("地埋式水位计", "位于", "山洪沟流域"),
            rule("地埋式水位计", "位于", "涉山涉水风景区"),
            rule("沟道巡查责任人", "负责", "河段"),
            rule("河道报讯站", "监测", "监测断面"),
            rule("河道报讯站", "位于", "山洪沟流域"),
            rule("河道实时站", "监测", "监测断面"),
            rule("河道实时站", "位于", "山洪沟流域"),
            rule("河道实时站", "位于", "险村"),
            rule("河段", "关联", "桥梁"),
            rule("河段", "关联", "塘坝_点数据"),
            rule("河段", "关联", "塘坝_面数据"),
            rule("河段", "关联", "险村"),
            rule("河段", "关联", "镇级行政区"),
            rule("河段", "流向", "监测断面"),
            rule("河段", "流向", "交汇点"),
            rule("河段", "流向", "水库_点数据"),
            rule("河段", "流向", "水库_面数据"),
            rule("河段", "流向", "塘坝_点数据"),
            rule("河段", "流向", "塘坝_面数据"),
            rule("河段", "流向", "预警断面"),
            rule("河段", "上游", "河段"),
            rule("河段", "属于", "山洪沟流域"),
            rule("河段", "属于", "涉山涉水风景区"),
            rule("河段", "下游", "河段"),
            rule("监测断面", "流向", "河段"),
            rule("交汇点", "流向", "河段"),
            rule("交汇点", "位于", "山洪沟流域"),
            rule("立即转移指标", "关联", "桥梁"),
            rule("立即转移指标", "关联", "塘坝_点数据"),
            rule("立即转移指标", "关联", "塘坝_面数据"),
            rule("立即转移指标", "关联", "险村"),
            rule("立即转移指标", "关联", "镇级行政区"),
            rule("路涵", "位于", "山洪沟流域"),
            rule("露营地", "位于", "山洪沟流域"),
            rule("企事业单位", "位于", "山洪沟流域"),
            rule("企事业单位", "位于", "危险区"),
            rule("桥梁", "包含", "墒情站"),
            rule("桥梁", "包含", "险户"),
            rule("桥梁", "上游", "桥梁"),
            rule("桥梁", "上游", "险村"),
            rule("桥梁", "属于", "村级行政区"),
            rule("桥梁", "位于", "山洪沟流域"),
            rule("桥梁", "位于", "镇级行政区"),
            rule("桥梁", "下游", "桥梁"),
            rule("桥梁", "下游", "险村"),
            rule("山洪沟流域", "包含", "地埋式水位计"),
            rule("山洪沟流域", "包含", "河道报讯站"),
            rule("山洪沟流域", "包含", "河道实时站"),
            rule("山洪沟流域", "包含", "河段"),
            rule("山洪沟流域", "包含", "交汇点"),
            rule("山洪沟流域", "包含", "路涵"),
            rule("山洪沟流域", "包含", "桥梁"),
            rule("山洪沟流域", "包含", "墒情站"),
            rule("山洪沟流域", "包含", "视频站"),
            rule("山洪沟流域", "包含", "水库_点数据"),
            rule("山洪沟流域", "包含", "水库_面数据"),
            rule("山洪沟流域", "包含", "水库报讯站"),
            rule("山洪沟流域", "包含", "水库实时站"),
            rule("山洪沟流域", "包含", "塘坝_点数据"),
            rule("山洪沟流域", "包含", "塘坝_面数据"),
            rule("山洪沟流域", "包含", "险村"),
            rule("山洪沟流域", "包含", "雨量站"),
            rule("山洪沟流域", "位于", "山洪沟流域"),
            rule("山洪沟流域", "位于", "险村"),
            rule("山洪沟流域", "位于", "镇级行政区"),
            rule("山洪沟流域雨量预警指标", "关联", "山洪沟流域"),
            rule("山洪沟流域雨量预警指标", "关联", "涉山涉水风景区"),
            rule("墒情站", "关联", "河段"),
            rule("墒情站", "监测", "河段"),
            rule("墒情站", "位于", "桥梁"),
            rule("墒情站", "位于", "山洪沟流域"),
            rule("墒情站", "位于", "涉山涉水风景区"),
            rule("墒情站", "位于", "险村"),
            rule("墒情站", "位于", "镇级行政区"),
            rule("涉山涉水风景区", "包含", "地埋式水位计"),
            rule("涉山涉水风景区", "包含", "河段"),
            rule("涉山涉水风景区", "包含", "墒情站"),
            rule("涉山涉水风景区", "包含", "雨量站"),
            rule("涉山涉水风景区", "位于", "山洪沟流域"),
            rule("涉山涉水风景区", "位于", "镇级行政区"),
            rule("视频站", "关联", "河段"),
            rule("视频站", "监测", "河段"),
            rule("视频站", "位于", "山洪沟流域"),
            rule("水库_点数据", "流向", "河段"),
            rule("水库_点数据", "位于", "山洪沟流域"),
            rule("水库_面数据", "流向", "河段"),
            rule("水库_面数据", "位于", "山洪沟流域"),
            rule("水库报讯站", "监测", "水库_点数据"),
            rule("水库报讯站", "监测", "水库_面数据"),
            rule("水库报讯站", "监测", "塘坝_点数据"),
            rule("水库报讯站", "位于", "山洪沟流域"),
            rule("水库实时站", "监测", "水库_点数据"),
            rule("水库实时站", "监测", "水库_面数据"),
            rule("水库实时站", "位于", "山洪沟流域"),
            rule("水位预警指标", "关联", "预警断面"),
            rule("塘坝_点数据", "包含", "险户"),
            rule("塘坝_点数据", "流向", "河段"),
            rule("塘坝_点数据", "属于", "村级行政区"),
            rule("塘坝_点数据", "位于", "山洪沟流域"),
            rule("塘坝_点数据", "位于", "镇级行政区"),
            rule("塘坝_面数据", "包含", "险户"),
            rule("塘坝_面数据", "流向", "河段"),
            rule("塘坝_面数据", "属于", "村级行政区"),
            rule("塘坝_面数据", "位于", "山洪沟流域"),
            rule("塘坝_面数据", "位于", "镇级行政区"),
            rule("危险区", "包含", "企事业单位"),
            rule("危险区", "包含", "险村"),
            rule("危险区", "包含", "镇级行政区"),
            rule("危险区", "位于", "山洪沟流域"),
            rule("危险区", "位于", "涉山涉水风景区"),
            rule("险村", "包含", "河道实时站"),
            rule("险村", "包含", "山洪沟流域"),
            rule("险村", "包含", "墒情站"),
            rule("险村", "包含", "险村"),
            rule("险村", "包含", "险户"),
            rule("险村", "包含", "镇级行政区"),
            rule("险村", "上游", "桥梁"),
            rule("险村", "上游", "险村"),
            rule("险村", "属于", "村级行政区"),
            rule("险村", "属于", "区级行政区"),
            rule("险村", "位于", "山洪沟流域"),
            rule("险村", "位于", "危险区"),
            rule("险村", "位于", "险村"),
            rule("险村", "位于", "镇级行政区"),
            rule("险村", "下游", "桥梁"),
            rule("险村", "下游", "险村"),
            rule("雨量站", "关联", "河段"),
            rule("雨量站", "监测", "桥梁"),
            rule("雨量站", "监测", "险村"),
            rule("雨量站", "监测", "镇级行政区"),
            rule("雨量站", "位于", "山洪沟流域"),
            rule("雨量站", "位于", "涉山涉水风景区"),
            rule("预案方案", "关联", "桥梁"),
            rule("预案方案", "关联", "塘坝_点数据"),
            rule("预案方案", "关联", "塘坝_面数据"),
            rule("预案方案", "关联", "险村"),
            rule("预案方案", "关联", "镇级行政区"),
            rule("预警断面", "关联", "桥梁"),
            rule("预警断面", "关联", "险村"),
            rule("预警断面", "流向", "河段"),
            rule("镇级行政区", "包含", "桥梁"),
            rule("镇级行政区", "包含", "山洪沟流域"),
            rule("镇级行政区", "包含", "墒情站"),
            rule("镇级行政区", "包含", "涉山涉水风景区"),
            rule("镇级行政区", "包含", "塘坝_点数据"),
            rule("镇级行政区", "包含", "塘坝_面数据"),
            rule("镇级行政区", "包含", "险村"),
            rule("镇级行政区", "包含", "险户"),
            rule("镇级行政区", "包含", "镇级行政区"),
            rule("镇级行政区", "属于", "村级行政区"),
            rule("镇级行政区", "属于", "区级行政区"),
            rule("镇级行政区", "位于", "危险区"),
            rule("镇级行政区", "位于", "险村"),
            rule("镇级行政区", "位于", "镇级行政区"),
            rule("转移安置责任人", "负责", "安置点"),
            rule("转移安置责任人", "负责", "转移路线"),
            rule("转移路线", "到达", "安置点"),
            rule("转移路线", "关联", "桥梁"),
            rule("转移路线", "关联", "塘坝_点数据"),
            rule("转移路线", "关联", "塘坝_面数据"),
            rule("转移路线", "关联", "险村"),
            rule("转移路线", "关联", "镇级行政区"),
            rule("准备转移指标", "关联", "桥梁"),
            rule("准备转移指标", "关联", "塘坝_点数据"),
            rule("准备转移指标", "关联", "塘坝_面数据"),
            rule("准备转移指标", "关联", "险村"),
            rule("准备转移指标", "关联", "镇级行政区")
    );

    private static final List<String> ENTITY_TYPES = RELATION_RULES.stream()
            .flatMap(rule -> List.of(rule.headType(), rule.tailType()).stream())
            .distinct()
            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(String::compareTo))
            .toList();

    private static final List<String> RELATION_TYPES = RELATION_RULES.stream()
            .map(GraphRelationRule::relationType)
            .distinct()
            .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(String::compareTo))
            .toList();

    private static final Map<String, List<String>> TYPE_ALIASES = Map.ofEntries(
            Map.entry("区县", List.of("区级行政区")),
            Map.entry("区", List.of("区级行政区")),
            Map.entry("乡镇", List.of("镇级行政区")),
            Map.entry("镇", List.of("镇级行政区")),
            Map.entry("村", List.of("险村", "村级行政区")),
            Map.entry("险村", List.of("险村")),
            Map.entry("险户", List.of("险户")),
            Map.entry("负责人", List.of("村级责任人", "沟道巡查责任人", "转移安置责任人")),
            Map.entry("责任人", List.of("村级责任人", "沟道巡查责任人", "转移安置责任人")),
            Map.entry("巡查责任人", List.of("沟道巡查责任人")),
            Map.entry("转移安置责任人", List.of("转移安置责任人")),
            Map.entry("流域", List.of("山洪沟流域")),
            Map.entry("山洪沟", List.of("山洪沟流域")),
            Map.entry("河流", List.of("河段")),
            Map.entry("河道", List.of("河段")),
            Map.entry("河段", List.of("河段")),
            Map.entry("水库", List.of("水库_点数据", "水库_面数据")),
            Map.entry("塘坝", List.of("塘坝_点数据", "塘坝_面数据")),
            Map.entry("桥", List.of("桥梁")),
            Map.entry("桥梁", List.of("桥梁")),
            Map.entry("路涵", List.of("路涵")),
            Map.entry("露营地", List.of("露营地")),
            Map.entry("企业", List.of("企事业单位")),
            Map.entry("单位", List.of("企事业单位")),
            Map.entry("风景区", List.of("涉山涉水风景区")),
            Map.entry("景区", List.of("涉山涉水风景区")),
            Map.entry("危险区", List.of("危险区")),
            Map.entry("雨量站", List.of("雨量站")),
            Map.entry("视频站", List.of("视频站")),
            Map.entry("墒情站", List.of("墒情站")),
            Map.entry("水位计", List.of("地埋式水位计")),
            Map.entry("地埋水位计", List.of("地埋式水位计")),
            Map.entry("监测断面", List.of("监测断面")),
            Map.entry("预警断面", List.of("预警断面")),
            Map.entry("转移路线", List.of("转移路线")),
            Map.entry("路线", List.of("转移路线")),
            Map.entry("安置点", List.of("安置点")),
            Map.entry("预案", List.of("预案方案")),
            Map.entry("指标", List.of("准备转移指标", "立即转移指标", "水位预警指标", "山洪沟流域雨量预警指标"))
    );

    public List<GraphRelationRule> allRules() {
        return RELATION_RULES;
    }

    public List<String> entityTypes() {
        return ENTITY_TYPES;
    }

    public List<String> relationTypes() {
        return RELATION_TYPES;
    }

    public RelationQuestion parseQuestion(String question) {
        String normalizedQuestion = normalizeQuestion(question);
        String sourceName = extractSourceName(normalizedQuestion);
        String targetText = extractTargetText(normalizedQuestion);
        List<String> targetTypes = detectTargetTypes(StringUtils.hasText(targetText) ? targetText : normalizedQuestion);
        List<String> relationTypes = detectRelationTypes(normalizedQuestion);

        if (relationTypes.isEmpty() && !targetTypes.isEmpty()) {
            relationTypes = inferRelationTypesByTailTypes(targetTypes);
        }

        return new RelationQuestion(sourceName, relationTypes, targetTypes);
    }

    public boolean isStructuredRelationQuestion(String question) {
        RelationQuestion parsed = parseQuestion(question);
        if (!StringUtils.hasText(parsed.sourceName())) {
            return false;
        }
        return !parsed.relationTypes().isEmpty() || !parsed.targetTypes().isEmpty();
    }

    public String formatFullSchema() {
        Map<String, List<GraphRelationRule>> groupedRules = RELATION_RULES.stream()
                .collect(Collectors.groupingBy(
                        GraphRelationRule::relationType,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));

        StringBuilder sb = new StringBuilder();
        sb.append("GRAPH_RELATION_SCHEMA\n");
        sb.append("实体类型总数：").append(ENTITY_TYPES.size()).append("\n");
        sb.append("关系类型总数：").append(RELATION_TYPES.size()).append("\n");
        sb.append("展开关系规则总数：").append(RELATION_RULES.size()).append("\n\n");

        groupedRules.forEach((relationType, rules) -> {
            sb.append("【").append(relationType).append("】").append(rules.size()).append("条\n");
            for (GraphRelationRule rule : rules) {
                sb.append("- ")
                        .append(rule.headType())
                        .append(" -[")
                        .append(rule.relationType())
                        .append("]-> ")
                        .append(rule.tailType())
                        .append("\n");
            }
            sb.append("\n");
        });
        return sb.toString();
    }

    private List<String> detectTargetTypes(String question) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (String entityType : ENTITY_TYPES) {
            if (question.contains(entityType)) {
                types.add(entityType);
            }
        }
        TYPE_ALIASES.forEach((alias, mappedTypes) -> {
            if (question.contains(alias)) {
                types.addAll(mappedTypes);
            }
        });
        return new ArrayList<>(types);
    }

    private List<String> detectRelationTypes(String question) {
        LinkedHashSet<String> types = new LinkedHashSet<>();
        for (String relationType : RELATION_TYPES) {
            if (question.contains(relationType)) {
                types.add(relationType);
            }
        }
        if (containsAny(question, "负责人", "责任人", "谁负责", "电话")) {
            types.add("负责");
        }
        if (containsAny(question, "监测", "测站", "站点覆盖")) {
            types.add("监测");
        }
        if (containsAny(question, "在哪里", "在哪", "位于哪里", "属于哪里", "哪个区", "哪个镇", "归属")) {
            types.add("位于");
            types.add("属于");
        }
        if (containsAny(question, "包含", "有哪些", "有多少", "多少个", "几个")) {
            types.add("包含");
        }
        if (containsAny(question, "到哪里", "到达", "去哪个安置点")) {
            types.add("到达");
        }
        return new ArrayList<>(types);
    }

    private List<String> inferRelationTypesByTailTypes(List<String> targetTypes) {
        Set<String> targetTypeSet = new LinkedHashSet<>(targetTypes);
        return RELATION_RULES.stream()
                .filter(rule -> targetTypeSet.contains(rule.tailType()))
                .map(GraphRelationRule::relationType)
                .distinct()
                .toList();
    }

    private String extractSourceName(String question) {
        String text = removeQuestionPrefix(question);
        String[] markers = {
                "有哪些", "有多少", "多少个", "几个", "谁负责", "由谁负责", "负责人", "责任人",
                "属于哪里", "位于哪里", "在哪里", "在哪", "属于哪个", "位于哪个",
                "上游", "下游", "包含", "属于", "位于", "关联", "监测", "流向", "到达",
                "的信息", "的情况", "情况", "信息"
        };
        for (String marker : markers) {
            int index = text.indexOf(marker);
            if (index > 0) {
                return cleanSourceName(text.substring(0, index));
            }
        }
        return "";
    }

    private String removeQuestionPrefix(String question) {
        return normalizeQuestion(question)
                .replaceFirst("^(请问|帮我查一下|帮我查|查询一下|查询|查一下|查|我想知道|我想查|看看|分析一下|分析)", "")
                .trim();
    }

    private String cleanSourceName(String sourceName) {
        return Objects.toString(sourceName, "")
                .replace("的", "")
                .replace("？", "")
                .replace("?", "")
                .replace("，", "")
                .replace(",", "")
                .trim();
    }

    private String extractTargetText(String question) {
        String text = removeQuestionPrefix(question);
        String[] markers = {
                "有哪些", "有多少", "多少个", "几个", "属于哪个", "位于哪个",
                "属于哪里", "位于哪里", "在哪里", "在哪", "谁负责", "由谁负责",
                "上游", "下游", "包含", "属于", "位于", "关联", "监测", "流向", "到达"
        };
        for (String marker : markers) {
            int index = text.indexOf(marker);
            if (index >= 0 && index + marker.length() < text.length()) {
                return text.substring(index + marker.length())
                        .replace("？", "")
                        .replace("?", "")
                        .trim();
            }
            if (index >= 0) {
                return marker;
            }
        }
        return "";
    }

    private String normalizeQuestion(String question) {
        return Objects.toString(question, "")
                .replace('（', '(')
                .replace('）', ')')
                .trim();
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static GraphRelationRule rule(String headType, String relationType, String tailType) {
        return new GraphRelationRule(headType, relationType, tailType);
    }

    public record RelationQuestion(String sourceName, List<String> relationTypes, List<String> targetTypes) {
    }
}
