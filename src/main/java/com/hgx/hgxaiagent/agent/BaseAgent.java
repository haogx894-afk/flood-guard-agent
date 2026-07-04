package com.hgx.hgxaiagent.agent;

import cn.hutool.core.util.StrUtil;
import com.hgx.hgxaiagent.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 抽象基础代理类，用于管理代理状态、消息记忆和执行流程。
 */
@Data
@Slf4j
public abstract class BaseAgent {

    private String name;

    private String systemPrompt;
    private String nextStepPrompt;

    private AgentState state = AgentState.IDLE;

    private int currentStep = 0;
    private int maxSteps = 10;

    private ChatClient chatClient;

    /**
     * 智能体自己维护的多轮对话上下文。
     */
    private List<Message> messageList = new ArrayList<>();

    /**
     * SSE 异步执行结束后，用它把最新 messageList 保存到外部缓存。
     */
    private Consumer<List<Message>> memorySaver;

    public String run(String userPrompt) {
        validateUserPrompt(userPrompt);

        this.state = AgentState.RUNNING;
        messageList.add(new UserMessage(userPrompt));

        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("Executing step {}/{}", stepNumber, maxSteps);

                String stepResult = step();
                String output = formatStepOutput(stepNumber, stepResult);
                results.add(output);
            }

            if (currentStep >= maxSteps) {
                state = AgentState.FINISHED;
                results.add("执行结束：达到最大步骤数（" + maxSteps + "）");
            }
            return String.join("\n", results);
        } catch (Exception e) {
            state = AgentState.ERROR;
            log.error("error executing agent", e);
            return "执行错误：" + e.getMessage();
        } finally {
            saveMemory();
            cleanup();
        }
    }

    public SseEmitter runStream(String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                validateUserPrompt(userPrompt);

                this.state = AgentState.RUNNING;
                messageList.add(new UserMessage(userPrompt));

                for (int i = 0; i < maxSteps && state != AgentState.FINISHED; i++) {
                    int stepNumber = i + 1;
                    currentStep = stepNumber;
                    log.info("Executing step {}/{}", stepNumber, maxSteps);

                    String stepResult = step();
                    String output = formatStepOutput(stepNumber, stepResult);
                    sseEmitter.send(output);
                }

                if (currentStep >= maxSteps) {
                    state = AgentState.FINISHED;
                    sseEmitter.send("执行结束：达到最大步骤数（" + maxSteps + "）");
                }
                sseEmitter.complete();
            } catch (Exception e) {
                state = AgentState.ERROR;
                log.error("error executing agent", e);
                try {
                    sseEmitter.send("执行错误：" + e.getMessage());
                    sseEmitter.complete();
                } catch (IOException ex) {
                    sseEmitter.completeWithError(ex);
                }
            } finally {
                saveMemory();
                cleanup();
            }
        });

        sseEmitter.onTimeout(() -> {
            this.state = AgentState.ERROR;
            cleanup();
            log.warn("SSE connection timeout");
        });

        sseEmitter.onCompletion(() -> {
            if (this.state == AgentState.RUNNING) {
                this.state = AgentState.FINISHED;
            }
            cleanup();
            log.info("SSE connection completed");
        });

        return sseEmitter;
    }

    private void validateUserPrompt(String userPrompt) {
        if (this.state != AgentState.IDLE) {
            throw new RuntimeException("无法从当前状态运行智能体：" + this.state);
        }
        if (StrUtil.isBlank(userPrompt)) {
            throw new RuntimeException("不能使用空提示词运行智能体");
        }
    }

    private String formatStepOutput(int stepNumber, String stepResult) {
        if (shouldShowStepPrefix()) {
            return "Step " + stepNumber + ": " + stepResult;
        }
        return stepResult;
    }

    /**
     * 子类可以按当前步骤的类型决定是否显示 Step 前缀。
     */
    protected boolean shouldShowStepPrefix() {
        return true;
    }

    public abstract String step();

    protected void cleanup() {
        // 子类可以重写此方法来清理资源
    }

    protected void saveMemory() {
        if (this.memorySaver != null) {
            this.memorySaver.accept(new ArrayList<>(this.messageList));
        }
    }
}
