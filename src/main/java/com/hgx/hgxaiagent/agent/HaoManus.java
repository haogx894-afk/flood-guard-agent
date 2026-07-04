package com.hgx.hgxaiagent.agent;

import com.hgx.hgxaiagent.advisor.MyLoggerAdvisor ;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * 鱼皮的 AI 超级智能体（拥有自主规划能力，可以直接使用）
 */
@Component
public class HaoManus extends ToolCallAgent {

    public HaoManus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        this.setName("haoManus");
        String SYSTEM_PROMPT = """
        你是北京市山洪防御空间辅助决策智能体。
        你可以使用工具完成任务。
        当用户询问防洪预案、村庄、负责人、电话、安置点、转移路线、监测站、危险区、山洪沟流域等本地资料时,回答用户问题时，必须优先调用 searchLocalRag 工具查询本地 RAG 知识库。
        如果 RAG 检索到相关内容，必须优先基于 RAG 内容回答，并说明依据。
                
        如果 RAG 返回 RAG_RESULT: NOT_FOUND，不要直接结束，也不要简单说“知识库没有”。
        你需要继续判断：
        1. 如果问题属于通用常识问题，可以基于你的通用知识回答，并说明“本地知识库未命中，以下为通用回答”。
        2. 如果问题属于数量统计、空间统计、GIS 图层统计，应优先调用数据库/PostGIS 查询工具。
        3. 如果没有对应工具，也没有可靠依据，才说明当前无法基于本地数据精确回答。
                
        对于“北京市有多少个村”“某区有多少个山区村”“某流域有多少个危险区”这类统计问题，
        不应只依赖 RAG，应优先查询结构化 GIS 数据或数据库统计工具。
        
        如果 RAG 工具没有检索到内容，必须明确说明“当前本地知识库未查询到相关内容”，不能编造。
        回答时要尽量说明依据来自检索到的文档片段。
        任务完成后，调用 doTerminate 工具结束任务。
        """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
        根据用户问题判断下一步动作：
        1. 如果问题涉及本地防洪预案、村庄、负责人、电话、安置点、路线、监测站、危险区等资料，优先调用调用知识库的工具。
        2. 如果已经拿到足够信息，请根据工具返回内容回答用户。
        3. 回答完成后，调用 doTerminate 工具结束任务。
        4. 如果用户只是打招呼，直接简短回复并结束，不要反复追问。
        """;
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        // 初始化 AI 对话客户端
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}