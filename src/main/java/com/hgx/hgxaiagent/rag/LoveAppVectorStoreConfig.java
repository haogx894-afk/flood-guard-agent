package com.hgx.hgxaiagent.rag;

import com.hgx.hgxaiagent.rag.LoveAppDocumentLoader;
import jakarta.annotation.Resource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储配置类
 * 构造一个 VectorStore 对象，用于存储和检索向量数据
 */
@Configuration
public class LoveAppVectorStoreConfig {

    @Resource
    private LoveAppDocumentLoader loveAppDocumentLoader;

//    @Bean
    VectorStore appVectorStore(EmbeddingModel dashscopeEmbeddingModel) {
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(dashscopeEmbeddingModel)
                .build();

        // 读取 document 目录下的 PDF 文件，向量化后存入向量库
        simpleVectorStore.add(loveAppDocumentLoader.loadPdfs());
        // 读取 document 目录下的 Markdown 文件，向量化后存入向量库
        simpleVectorStore.add(loveAppDocumentLoader.loadMarkdowns());
        return simpleVectorStore;
    }

}
