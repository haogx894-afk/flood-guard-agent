package com.hgx.hgxaiagent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 查询重写器，就是利用大模型优化用户的prompt，让prompt保留关键信息
 */
@Component
public class QueryRewriter {

    private final QueryTransformer queryTransformer;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        this.queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(
                        ChatClient.builder(dashscopeChatModel)
                )
                .build();
    }

    /**
     * 查询重写
     *
     * @param prompt 用户输入的查询
     * @return 重写后的文本
     */
    public String doQueryRewrite(String prompt) {
        return queryTransformer.transform(new Query(prompt)).text();
    }

}
