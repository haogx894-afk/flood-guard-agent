package com.hgx.hgxaiagent.tools;

import com.hgx.hgxaiagent.knowledgegraph.service.GraphReasoningService;
import com.hgx.hgxaiagent.knowledgegraph.service.KnowledgeGraphService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.vectorstore.VectorStore;
/**
 * 集中的工具注册类，有工具了，得跟spring ai说工具都在这，用的时候就调用
 */
@Configuration
public class ToolRegistration {

    @Value("${search-api.api-key}")
    private String searchApiKey;

    @Bean
    public ToolCallback[] allTools(
            @Qualifier("pgVectorVectorStore") VectorStore pgVectorVectorStore,
            KnowledgeGraphService knowledgeGraphService,
            GraphReasoningService graphReasoningService) {
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        WebScrapingTool webScrapingTool = new WebScrapingTool();
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        TerminalOperationTool terminalOperationTool = new TerminalOperationTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();
        //使用本地向量数据库
        LocalRagTool localRagTool = new LocalRagTool(pgVectorVectorStore);
        //使用neo4j知识图谱
        KnowledgeGraphTool knowledgeGraphTool = new KnowledgeGraphTool(knowledgeGraphService, graphReasoningService);

        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                webScrapingTool,
                resourceDownloadTool,
                terminalOperationTool,
                pdfGenerationTool,
                terminateTool,
                localRagTool,
                knowledgeGraphTool
        );
    }
}
