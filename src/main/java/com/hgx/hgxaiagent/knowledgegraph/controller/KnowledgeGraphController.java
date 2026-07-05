package com.hgx.hgxaiagent.knowledgegraph.controller;

import com.hgx.hgxaiagent.knowledgegraph.model.GraphData;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphEdge;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphHealth;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphNode;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphSearchResult;
import com.hgx.hgxaiagent.knowledgegraph.model.GraphStats;
import com.hgx.hgxaiagent.knowledgegraph.service.KnowledgeGraphService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/knowledge/graph")
public class KnowledgeGraphController {

    private final KnowledgeGraphService knowledgeGraphService;

    public KnowledgeGraphController(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @GetMapping("/health")
    public GraphHealth health() {
        return knowledgeGraphService.health();
    }

    @GetMapping("/stats")
    public GraphStats stats() {
        return knowledgeGraphService.stats();
    }

    @GetMapping("/search")
    public GraphSearchResult search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "100") int limit) {
        return knowledgeGraphService.search(keyword, limit);
    }

    @GetMapping("/nodes/{nodeId}")
    public GraphNode getNode(@PathVariable String nodeId) {
        return knowledgeGraphService.getNode(nodeId);
    }

    @GetMapping("/relationships/{relationshipId}")
    public GraphEdge getRelationship(@PathVariable String relationshipId) {
        return knowledgeGraphService.getRelationship(relationshipId);
    }

    @GetMapping("/visualize")
    public GraphData visualize(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int depth,
            @RequestParam(defaultValue = "300") int limit) {
        return knowledgeGraphService.visualize(keyword, depth, limit);
    }
}
