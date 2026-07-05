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
        同时，你已经接入 Neo4j 知识图谱。知识图谱中存储了实体与实体之间的关系，例如行政区、镇、村、险村、危险区、河流、山洪沟流域、安置点、转移路线、监测站、负责人、上下游、包含、属于、位于、负责等关系。
        当用户询问实体关系、上下游、所属区域、所属流域、包含对象、负责人、监测关系、到达关系、关联对象、实体数量、关系数量等问题时，应调用 searchKnowledgeGraph 或 getKnowledgeGraphStats 工具查询知识图谱。
        RAG 适合查询预案原文和文档片段；知识图谱适合查询结构化实体、关系和统计。两者都可能有用时，应同时结合 RAG 与知识图谱结果回答。
                
        如果 RAG 返回 RAG_RESULT: NOT_FOUND，不要直接结束，也不要简单说“知识库没有”。
        你需要继续判断：
        1. 如果问题属于通用常识问题，可以基于你的通用知识回答，并说明“本地知识库未命中，以下为通用回答”。
        2. 如果问题属于数量统计、实体关系统计、空间统计、GIS 图层统计，应优先调用知识图谱工具或数据库/PostGIS 查询工具。
        3. 如果没有对应工具，也没有可靠依据，才说明当前无法基于本地数据精确回答。
                
        对于“北京市有多少个村”“某区有多少个山区村”“某流域有多少个危险区”这类统计问题，
        不应只依赖 RAG，应优先查询知识图谱、结构化 GIS 数据或数据库统计工具。
        
        如果 RAG 工具没有检索到内容，必须明确说明“当前本地知识库未查询到相关内容”，不能编造。
        回答时要尽量说明依据来自检索到的文档片段。
        任务完成后，调用 doTerminate 工具结束任务。
        """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        String NEXT_STEP_PROMPT = """
        根据用户问题判断下一步动作：
        1. 如果问题涉及本地防洪预案、村庄、负责人、电话、安置点、路线、监测站、危险区等资料，优先调用调用知识库的工具。
        2. 如果问题涉及实体关系、上下游、属于哪里、包含哪些对象、谁负责、数量统计、图谱统计，调用知识图谱工具。
        3. 如果问题既需要预案原文依据，又需要实体关系依据，可以同时调用 RAG 工具和知识图谱工具。
        4. 如果已经拿到足够信息，请根据工具返回内容回答用户。
        5. 回答完成后，调用 doTerminate 工具结束任务。
        6. 如果用户只是打招呼，直接简短回复并结束，不要反复追问。
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
