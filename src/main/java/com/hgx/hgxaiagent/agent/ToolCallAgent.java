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
 * think 负责让大模型决定是否调用工具，act 负责真正执行工具。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private final ToolCallback[] availableTools;

    /**
     * 保存模型返回的工具调用响应，act 阶段会继续使用。
     */
    private ChatResponse toolCallChatResponse;

    /**
     * 保存模型直接生成的文本。
     * 如果本轮不需要工具，或者模型调用 doTerminate 结束任务，就把它作为最终回答返回给前端。
     */
    private String lastAssistantMessageText;

    /**
     * 标记当前 step 的输出是不是最终回答。
     * true：前端不显示 Step 前缀；false：前端显示 Step n。
     */
    private boolean finalAnswerStep = false;

    private final ToolCallingManager toolCallingManager;

    /**
     * 禁用 Spring AI 内置工具调用机制，由当前类自己维护工具调用流程。
     */
    private final ChatOptions chatOptions;

    public ToolCallAgent(ToolCallback[] availableTools) {
        super();
        this.availableTools = availableTools;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder().withProxyToolCalls(true).build();
    }

    @Override
    public String step() {
        this.finalAnswerStep = false;
        try {
            boolean shouldAct = think();
            if (!shouldAct) {
                if (getState() != AgentState.ERROR) {
                    setState(AgentState.FINISHED);
                }
                this.finalAnswerStep = true;
                return StrUtil.blankToDefault(lastAssistantMessageText, "任务已完成。");
            }
            return act();
        } catch (Exception e) {
            setState(AgentState.ERROR);
            log.error("步骤执行失败", e);
            return "步骤执行失败：" + e.getMessage();
        }
    }

    @Override
    protected boolean shouldShowStepPrefix() {
        return !this.finalAnswerStep;
    }

    /**
     * 思考阶段：调用大模型，让模型判断是否需要调用工具。
     */
    @Override
    public boolean think() {
        if (StrUtil.isNotBlank(getNextStepPrompt())) {
            UserMessage userMessage = new UserMessage(getNextStepPrompt());//添加下一步的提示词到用户消息中
            getMessageList().add(userMessage);//把这次对话添加到用户对话消息列表中
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
            this.lastAssistantMessageText = assistantMessage.getText();

            log.info("{} 的思考：{}", getName(), this.lastAssistantMessageText);
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

    /**
     * 行动阶段：执行模型选择的工具，并把工具返回结果写入消息上下文。
     */
    @Override
    public String act() {
        if (toolCallChatResponse == null || !toolCallChatResponse.hasToolCalls()) {
            this.finalAnswerStep = true;
            return StrUtil.blankToDefault(lastAssistantMessageText, "任务已完成。");
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
            this.finalAnswerStep = true;
            return StrUtil.blankToDefault(lastAssistantMessageText, "任务已完成。");
        }

        String results = toolResponseMessage.getResponses()
                .stream()
                .map(response -> "工具 " + response.name() + " 返回的结果：" + response.responseData())
                .collect(Collectors.joining("\n"));

        log.info(results);
        return results;
    }
}
