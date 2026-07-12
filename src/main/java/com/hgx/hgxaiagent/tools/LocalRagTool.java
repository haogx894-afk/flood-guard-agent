package com.hgx.hgxaiagent.tools;

import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

/**
 * 本地 RAG 知识库查询工具
 * 作用：让智能体通过工具调用的方式，查询 PostgreSQL pgvector 中的向量数据。
 */
public class LocalRagTool {

    /**
     * 向量数据库操作对象
     * 实际注入的是 PgVectorVectorStoreConfig 中配置的 pgVectorVectorStore。
     */
    private final VectorStore vectorStore;

    /**
     * 构造方法注入 VectorStore
     * @param vectorStore 本地 pgvector 向量库对象
     */
    public LocalRagTool(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * 暴露给大模型调用的工具方法
     * 当模型判断用户问题需要查询本地知识库时，会调用这个方法。
     */
    @Tool(description = """
            查询本地 RAG 知识库，数据存储在 PostgreSQL pgvector 中。
            当用户询问防洪预案、村庄、负责人、电话、安置点、转移路线、监测站、危险区等本地资料时，必须优先调用本工具。
            """)
    public String searchLocalRag(
            @ToolParam(description = "用户问题或检索关键词") String query) {

        // 根据用户问题生成向量，并在 pgvector 中查找最相似的 5 个文档片段
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(3)
                        .build()
        );

        // 如果没有检索到相关内容，明确返回“未检索到”，避免模型胡编
        if (documents == null || documents.isEmpty()) {
            return """
            RAG_RESULT: NOT_FOUND
            本地 RAG 知识库未检索到直接相关内容。
            这不代表问题不能回答，请继续判断是否可以使用通用知识、数据库工具或其他工具回答。
            """;
        }

        // 拼接检索结果，返回给大模型，让模型基于这些内容组织最终回答
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("从本地 RAG 知识库检索到以下内容：\n\n");

        for (int i = 0; i < documents.size(); i++) {
            Document document = documents.get(i);

            stringBuilder.append("【文档片段 ").append(i + 1).append("】\n");

            // score 是相似度分数，用来辅助判断这个片段和问题的相关程度
            stringBuilder.append("相似度：").append(document.getScore()).append("\n");

            // metadata 通常包含文件名、页码等信息，方便回答时说明依据来源
            stringBuilder.append("元数据：").append(document.getMetadata()).append("\n");

            // text 是真正检索出来的文档内容
            stringBuilder.append("内容：").append(document.getText()).append("\n\n");
        }

        return stringBuilder.toString();
    }
}