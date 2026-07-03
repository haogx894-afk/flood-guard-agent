package com.hgx.hgxaiagent.app;


import com.github.xiaoymin.knife4j.spring.annotations.EnableKnife4j;
import com.hgx.hgxaiagent.advisor.MyLoggerAdvisor;
import com.hgx.hgxaiagent.advisor.ReReadingAdvisor;
import com.hgx.hgxaiagent.chatmemory.FileBasedChatMemory;
import com.hgx.hgxaiagent.rag.LoveAppContextualQueryAugmenterFactory;
import com.hgx.hgxaiagent.rag.LoveAppRagCustomAdvisorFactory;
import com.hgx.hgxaiagent.rag.LoveAppVectorStoreConfig;
import com.hgx.hgxaiagent.rag.QueryRewriter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
//import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
//import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
//import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;


@Component //LoveApp 上有 @Component，所以 Spring 会自动创建 LoveApp 对象
@Slf4j
public class LoveApp {

    //先初始化一个ai客户端
    private final ChatClient chatClient;
    //定义静态常量 用于设置系统提示词
//    private static final String SYSTEM_PROMPT = """
//            你是一位专业、温柔、理性且有边界感的恋爱关系顾问。
//
//            用户会向你咨询与感情有关的问题，可能涉及单身、暧昧、恋爱、分手、复合、异地恋、婚姻、夫妻相处、亲密关系、沟通矛盾、情绪困扰等场景。
//
//            你的任务是：
//            1. 先理解用户的真实困扰，给予情绪上的接纳和安抚。
//            2. 根据用户所处的关系状态，分析问题背后的原因。
//            3. 给出具体、可执行、适合用户情况的解决方案。
//            4. 如果信息不足，主动追问关键问题，而不是直接下结论。
//            5. 必要时提供可以直接使用的聊天话术、沟通模板、行动步骤。
//            6. 帮助用户建立健康的亲密关系，而不是教用户操控、欺骗、冷暴力或PUA他人。
//
//            回答原则：
//            - 语气温柔、真诚、像一个成熟可靠的恋爱导师。
//            - 不站在单一性别立场攻击任何一方。
//            - 不鼓励卑微讨好、死缠烂打、控制欲、试探、报复。
//            - 尊重用户和对方的边界、意愿与人格。
//            - 如果涉及家暴、威胁、严重控制、自伤、自杀等风险，优先建议用户保护自身安全，并寻求现实中的专业帮助或可信任的人支持。
//
//            回答结构尽量包括：
//            1. 先共情用户的感受。
//            2. 再分析当前问题。
//            3. 给出具体建议。
//            4. 如果适合，提供一段可直接发送给对方的话术。
//            5. 最后提出一个关键问题，帮助继续深入分析。""";

    private static final String SYSTEM_PROMPT = """
            你是“北京市山洪防御空间辅助决策智能体”，专门服务于北京市山洪灾害防御、空间查询、预案辅助、转移研判和风险对象排查。
            
            你的核心职责是：基于用户提供的问题，结合 GIS 空间数据、防洪预案、山洪沟流域数据、行政区划数据、危险区、山区村、安置点、转移路线、监测站、桥梁、路涵、河流、水库、景区、露营地和企事业单位等信息，帮助用户进行山洪防御辅助决策。
            
            你不是正式预警发布系统，也不是防汛指挥机关。你的回答只能作为辅助分析和决策参考。涉及人员转移、封控、预警发布、应急响应等级等内容时，必须提醒用户以属地防汛部门、应急管理部门和正式预案要求为准。
            
            你可以支持以下类型的问题：
            
            
            1. 流域“一张图”查询
            - 查询某个山洪沟流域属于哪个区、哪个镇、哪个五大流域。
            - 查询某流域内有哪些山区村、险村、安置点、监测站、视频站、雨量站、水位计。
            - 查询某个区内有哪些山洪沟、危险区、河流、水库和重点防御对象。
            - 查询某条河流、沟道、水库周边一定距离内的露营地、风景区、企事业单位、村庄和危险区。
            
            2. 防御场景推演
            当用户手动指定场景时，例如：
            - 假设某山洪沟发生险情；
            - 假设某危险区需要人员转移；
            - 假设某座桥梁无法通行；
            - 假设某个水库周边需要重点巡查；
            你需要围绕该场景分析：
            - 涉及的行政区、乡镇、村庄和流域；
            - 周边山区村、险村、危险区；
            - 可用安置点和转移路线；
            - 转移路线是否经过河流、桥梁、路涵或危险区；
            - 附近雨量站、水位站、水位计、视频站、预警断面；
            - 涉及的景区、露营地、企事业单位等重点对象；
            - 预案中规定的处置措施、责任要求和注意事项。
            
            3. 转移路线检查
            你可以帮助用户检查：
            - 哪些村没有关联安置点；
            - 哪些村没有转移路线；
            - 转移路线是否穿过危险区；
            - 转移路线是否依赖某座桥梁或路涵；
            - 安置点是否位于山洪沟流域、河道附近或危险区内；
            - 某座桥梁、路涵无法通行时，哪些村的转移可能受影响。
            
            4. 监测站覆盖分析
            你可以帮助用户分析：
            - 每个山洪沟流域有哪些监测站；
            - 哪些流域没有雨量站、水位站、水位计或视频站；
            - 站点距离河流、沟道、预警断面、危险区有多远；
            - 哪些危险区附近缺少视频站；
            - 哪些区、镇或流域的监测站密度较低；
            - 哪些重点村庄、景区、露营地周边监测能力较弱。
            
            5. 预案与 GIS 一致性检查
            你可以帮助用户检查：
            - 预案中提到的村庄、河流、站点、单位是否存在于 GIS 数据中；
            - GIS 中的安置点、转移路线、危险区是否写入预案；
            - 村庄是否缺少转移路线或安置点；
            - 预案名称与 GIS 名称是否一致；
            - 预案中提到的单位、站点、村庄是否可能已经变化；
            - 发现不一致时，需要列出“不一致项、GIS 数据、预案内容、建议核查方式”。
            
            回答要求：
            1. 必须先判断用户问题属于哪类任务：流域查询、防御推演、路线检查、监测覆盖分析、预案一致性检查或综合分析。
            2. 如果用户提供的地名、流域名、村名、河流名不明确，应先指出可能存在重名或缺少定位信息，并建议用户补充区县、乡镇或具体图层对象。
            3. 如果已经查询到 GIS 或预案数据，要基于数据回答；如果没有查询到，必须明确说“当前数据中未查询到”，不能编造。
            4. 涉及距离、包含、相交、穿越、邻近等空间关系时，应尽量说明判断依据，例如“位于某流域内”“距离某河流约 X 公里”“路线穿过某危险区”。
            5. 涉及预案内容时，应说明依据来自哪份预案、哪个章节或哪类条款。如果没有检索到预案依据，要明确提示。
            6. 涉及风险和转移建议时，语气要谨慎，避免下达命令。使用“建议核查”“建议优先关注”“可作为辅助判断”“需由属地防汛责任人确认”等表达。
            7. 不得生成虚假的实时雨情、水情、预警等级或官方指令。当前系统没有实时监测数据时，只能进行静态空间分析、手动场景推演和预案辅助。
            8. 不要夸大结论。空间分析结果应服务于辅助决策，而不是替代专业部门判断。
            
            推荐回答结构：
            - 【问题类型】：说明这是哪类分析任务。
            - 【结论摘要】：用 2 到 4 条概括关键结论。
            - 【空间关联】：列出涉及的区、镇、流域、河流、危险区等。
            - 【重点对象】：列出山区村、险村、安置点、监测站、景区、露营地、企事业单位等。
            - 【转移与路线】：如涉及转移，说明安置点、路线、桥梁、路涵、危险区影响。
            - 【监测与巡查】：列出附近监测站、视频站、预警断面和建议关注点。
            - 【预案依据】：总结预案中的相关处置要求。
            - 【风险提示】：提醒用户该结果是辅助分析，正式行动以防汛部门和预案为准。
            - 【地图展示建议】：建议前端地图高亮哪些图层和对象。
            
            如果用户的问题适合地图展示，你应主动给出地图展示建议，例如：
            - 高亮山洪沟流域；
            - 高亮涉及的行政区、乡镇、村庄；
            - 显示危险区、安置点、转移路线；
            - 显示雨量站、水位站、视频站、预警断面；
            - 对路线穿越危险区、桥梁、路涵的位置进行标记。
            
            你的回答风格应专业、清晰、谨慎、结构化，适合防汛业务人员、基层工作人员和应急辅助决策人员阅读。
            """;


    /**
     * 选择具体模型，为阿里云的dashscopeChatModel
     * 初始化AI客户端
     *
     * @param dashscopeChatModel
     */


//    private static final String CHAT_MEMORY_PATH = System.getProperty("user.dir") + "/chat-memory";


    //用构造函数进行注入
    public LoveApp(ChatModel dashscopeChatModel) {
//        初始化基于内存的对话记忆

        ChatMemory chatMemory = new InMemoryChatMemory(); //创建一个聊天记忆对象，用来保存上下文。比如用户前面说过什么，AI 后面可以记得。
//        chatClient = ChatClient.builder(dashscopeChatModel) //用 dashscopeChatModel 创建一个聊天客户端。这个 chatClient 就是你以后真正用来和 AI 对话的对象。
//                .defaultSystem(SYSTEM_PROMPT) //设置系统提示词，也就是告诉 AI 它要扮演什么角色。
//                .defaultAdvisors(
//                        new MessageChatMemoryAdvisor(chatMemory)
//                ) //给 ChatClient 加一个“记忆增强器”，让它每次对话时可以使用 chatMemory。
//                .build();

        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(new InMemoryChatMemory()),
                        new MyLoggerAdvisor()
//                        new ReReadingAdvisor()
                )

                .defaultAdvisors(
                        advisorSpec -> advisorSpec.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10)
                )
                .build();
//
//        // 初始化基于文件的对话记忆
//        String fileDir = System.getProperty("user.dir") + "/chat-memory";
//        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
//        chatClient = ChatClient.builder(dashscopeChatModel).defaultSystem(SYSTEM_PROMPT).defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory)).build();
//

    }

    /**
     * 对话方法
     * AI基础对话（支持多轮对话记忆）
     *
     * @param message 对话内容
     * @param chatId  对话的id，这次对话的身份证
     * @return
     */
    public String doChat(String chatId, String message) {
        ChatResponse chatResponse = chatClient.prompt()     //准备开始组织一次提问
                .user(message)  //把用户输入的问题放进去
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId) //chatId = 当前聊天是哪一个会话  10 = 从记忆里取最近 10 条消息
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10)) //给这次聊天附加一些参数，比如使用哪个会话记忆
                .call() //真正调用 AI 模型
                .chatResponse(); //拿到完整的 AI 响应结果
        String context = chatResponse.getResult().getOutput().getText();//从 AI 的完整响应里取出文本内容 ,打印到日志,最后返回给调用者
        log.info("context: {}", context);
        return context;
    }

    record LoveReport(String title, List<String> suggestions) {
    }

    /**
     * 报告功能（实战结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        LoveReport loveReport = chatClient
                .prompt()
                .system(SYSTEM_PROMPT + "每次对话后都要生成山洪防御回答结果，标题为{用户名}的回答报告，内容为回答结果列表和建议列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(LoveReport.class);
        log.info("loveReport: {}", loveReport);
        return loveReport;
    }

    @Resource()
    private VectorStore loveAppVectorStore;

    /**
     * 和RAG知识库进行对话
     *
     * @param message
     * @param chatId
     * @return
     */

    @Resource
    private VectorStore pgVectorVectorStore;

    public String doChatWithRag(String message, String chatId) {
        //应用QueryRewriter查询重写器
        message = queryRewriter.doQueryRewrite(message);

        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(advisorSpec -> advisorSpec.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, chatId))
                //开启日志，便于观察结果
                .advisors(new MyLoggerAdvisor())
//                //应用RAG知识库进行问答
//                .advisors(new QuestionAnswerAdvisor(loveAppVectorStore))
                //基于RAG知识库 本地向量数据库进行问答
                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
//                // 应用自定义的 RAG 检索增强服务（文档查询器 + 上下文增强器）
//                .advisors(
//                        LoveAppRagCustomAdvisorFactory.createLoveAppRagCustomAdvisor(
//                              loveAppVectorStore,"张家坟村"
//                        )

                .call()
                .chatResponse();

        String context = chatResponse.getResult().getOutput().getText();
        log.info("context: {}", context);
        return context;
    }


    /**
     * 查询重写器
     */
    @Resource
    private QueryRewriter queryRewriter;

    public String doChatWithRagWithRewriter(String message, String chatId) {
        message = queryRewriter.doQueryRewrite(message);

        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(
                        advisorSpec -> advisorSpec.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                )
                //基于RAG知识库 本地向量数据库进行问答
                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .call()
                .chatResponse();
        String outputText = chatResponse.getResult().getOutput().getText();
        return outputText;
    }


    /**
     * 使用工具，让ai使用工具
     */
    @Resource
    private ToolCallback[] allTools;

    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }


    /**
     * 使用mcp
     */
    @Resource
    public ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    /**
     * AI 基础对话（支持多轮对话记忆，SSE 流式传输）
     *
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatByStream(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .stream()
                .content();
    }


}
