package com.hgx.hgxaiagent.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.hgx.hgxaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理工具调用的基础代理类。
 * think 负责让模型决定是否调用工具，act 负责真正执行工具。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    // 可用工具列表
    private final ToolCallback[] availableTools;

    // 保存模型返回的工具调用响应，act 阶段会继续使用
    private ChatResponse toolCallChatResponse;

    // 保存模型直接回复的文本；没有工具调用时直接返回给前端
    private String lastAssistantMessageText;

    // 工具调用管理器
    private final ToolCallingManager toolCallingManager;

    // 禁用 Spring AI 内置工具调用机制，由当前类自己维护工具调用流程
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder().withProxyToolCalls(true).build();
    }

    /**
     * 思考阶段：调用大模型，让模型判断是否需要调用工具。
     *
     * @return true 表示需要进入 act 执行工具，false 表示无需工具调用
     */
    @Override
    public boolean think() {
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }

        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);

        try {
            ChatResponse chatResponse = getChatClient()
                    .prompt(prompt)
                    .system(getSystemPrompt())
                    .tools(availableTools)
                    .call()
                    .chatResponse();

            this.toolCallChatResponse = chatResponse;

            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCallList = assistantMessage.getToolCalls();
            String result = assistantMessage.getText();
            this.lastAssistantMessageText = result;

            log.info("{} 的思考：{}", getName(), result);
            log.info("{} 选择了 {} 个工具来使用", getName(), toolCallList.size());

            String toolCallInfo = toolCallList.stream()
                    .map(toolCall -> String.format("工具名称：%s，参数：%s", toolCall.name(), toolCall.arguments()))
                    .collect(Collectors.joining("\n"));
            if (StrUtil.isNotBlank(toolCallInfo)) {
                log.info(toolCallInfo);
            }

            if (toolCallList.isEmpty()) {
                getMessageList().add(assistantMessage);
                setState(AgentState.FINISHED);
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("{} 的思考过程遇到了问题：{}", getName(), e.getMessage(), e);
            this.lastAssistantMessageText = "处理时遇到了错误：" + e.getMessage();
            getMessageList().add(new AssistantMessage(this.lastAssistantMessageText));
            setState(AgentState.ERROR);
            return false;
        }
    }


    //优化智能体回答
    /**
     * 单步执行。
     * 如果模型没有选择工具，就返回模型自己的回答，而不是返回“思考完成 - 无需行动”。
     */
    @Override
    public String step() {
        try {
            boolean shouldAct = think();
            if (!shouldAct) {
                if (getState() != AgentState.ERROR) {
                    setState(AgentState.FINISHED);
                }
                return StrUtil.blankToDefault(lastAssistantMessageText, "思考完成，无需调用工具。");
            }
            return act();
        } catch (Exception e) {
            setState(AgentState.ERROR);
            log.error("步骤执行失败", e);
            return "步骤执行失败：" + e.getMessage();
        }
    }

    /**
     * 行动阶段：执行模型选择的工具，并把工具返回结果写入消息上下文。
     *
     * @return 工具调用结果
     */
    @Override
    public String act() {
        if (toolCallChatResponse == null || !toolCallChatResponse.hasToolCalls()) {
            return "没有工具需要调用";
        }

        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallChatResponse);

        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        boolean terminateToolCalled = toolResponseMessage.getResponses()
                .stream()
                .anyMatch(response -> response.name().equals("doTerminate"));
        if (terminateToolCalled) {
            setState(AgentState.FINISHED);
        }

        String results = toolResponseMessage.getResponses()
                .stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));
        log.info(results);
        return results;
    }
}
