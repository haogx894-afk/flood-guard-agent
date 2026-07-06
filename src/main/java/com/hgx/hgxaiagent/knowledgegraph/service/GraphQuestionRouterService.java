package com.hgx.hgxaiagent.knowledgegraph.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图谱问题预路由。
 * 对于非常明确的结构化图谱查询，直接调用本地图谱推理，避免把大量工具 schema 交给大模型选择导致超时。
 */
@Service
public class GraphQuestionRouterService {

    private static final Pattern DISTRICT_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9（）()]+?区)");
    private static final Pattern TOWN_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9（）()]+?(?:满族乡|镇|乡))");
    private static final Pattern VILLAGE_WITH_BASIN_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]+(?:村|社区)[（(][\\u4e00-\\u9fa5A-Za-z0-9]+[）)])");
    private static final Pattern VILLAGE_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5A-Za-z0-9]+?(?:村|社区))");

    private final GraphReasoningService graphReasoningService;

    public GraphQuestionRouterService(GraphReasoningService graphReasoningService) {
        this.graphReasoningService = graphReasoningService;
    }

    public Optional<String> route(String message) {
        String question = normalize(message);
        if (!StringUtils.hasText(question)) {
            return Optional.empty();
        }

        Optional<String> householdAnswer = routeHouseholdQuestion(question);
        if (householdAnswer.isPresent()) {
            return householdAnswer;
        }

        Optional<String> riskVillageAnswer = routeRiskVillageQuestion(question);
        if (riskVillageAnswer.isPresent()) {
            return riskVillageAnswer;
        }

        return Optional.empty();
    }

    private Optional<String> routeHouseholdQuestion(String question) {
        if (!question.contains("险户")) {
            return Optional.empty();
        }
        if (!containsAny(question, "哪些", "多少", "几个", "列表", "名单", "清单", "信息", "情况")) {
            return Optional.empty();
        }

        Optional<String> villageName = extractVillageName(question);
        if (villageName.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(formatDirectGraphAnswer(
                "险村险户清单查询",
                graphReasoningService.queryHouseholdsByRiskVillage(villageName.get())
        ));
    }

    private Optional<String> routeRiskVillageQuestion(String question) {
        if (!question.contains("险村")) {
            return Optional.empty();
        }
        if (!containsAny(question, "哪些", "多少", "几个", "分布", "列表", "名单")) {
            return Optional.empty();
        }

        Optional<String> districtName = extract(question, DISTRICT_PATTERN);
        if (districtName.isPresent()) {
            return Optional.of(formatDirectGraphAnswer(
                    "区级行政区险村查询",
                    graphReasoningService.queryRiskVillagesByDistrict(simplifyRegionName(districtName.get(), "市"))
            ));
        }

        Optional<String> townName = extract(question, TOWN_PATTERN);
        if (townName.isPresent()) {
            return Optional.of(formatDirectGraphAnswer(
                    "镇级行政区险村查询",
                    graphReasoningService.queryRiskVillagesByTown(simplifyRegionName(townName.get(), "区"))
            ));
        }

        return Optional.empty();
    }

    private Optional<String> extractVillageName(String question) {
        String text = question
                .replaceFirst("^(请问|帮我查一下|帮我查|查询一下|查询|查一下|查|我想知道|我想查|看看)", "")
                .trim();

        String[] markers = {
                "有哪些险户", "有多少险户", "几个险户", "险户有哪些", "险户有多少",
                "的险户", "险户"
        };
        for (String marker : markers) {
            int index = text.indexOf(marker);
            if (index > 0) {
                String candidate = text.substring(0, index)
                        .replace("的", "")
                        .trim();
                if (StringUtils.hasText(candidate)) {
                    return Optional.of(candidate);
                }
            }
        }

        Optional<String> villageWithBasin = extract(text, VILLAGE_WITH_BASIN_PATTERN);
        if (villageWithBasin.isPresent()) {
            return villageWithBasin;
        }
        return extract(text, VILLAGE_PATTERN);
    }

    private String formatDirectGraphAnswer(String taskType, String graphResult) {
        return """
                【问题类型】：%s
                【数据来源】：Neo4j 知识图谱推理

                %s

                【说明】：该结果由后端按固定图谱路径直接查询得到，没有经过大模型自由猜测。涉及正式防汛行动时，仍需以属地防汛部门和正式预案为准。
                """.formatted(taskType, graphResult);
    }

    private Optional<String> extract(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1));
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String message) {
        return message == null ? "" : message.trim();
    }

    private String simplifyRegionName(String name, String separator) {
        int index = name.lastIndexOf(separator);
        if (index < 0 || index + 1 >= name.length()) {
            return name;
        }
        return name.substring(index + 1);
    }
}
