package com.hgx.hgxaiagent.controller;

import com.hgx.hgxaiagent.agent.HaoManus;
import com.hgx.hgxaiagent.app.LoveApp;
import com.hgx.hgxaiagent.knowledgegraph.service.GraphQuestionRouterService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/ai")
public class AiController {

    @Resource
    private LoveApp loveApp;

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private GraphQuestionRouterService graphQuestionRouterService;

    /**
     * Manus 多轮对话记忆。
     * key 是前端传入的 chatId，value 是该会话的历史消息。
     */
    private final Map<String, List<Message>> manusMemoryMap = new ConcurrentHashMap<>();

    /**
     * 同步调用 AI 恋爱大师应用
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message, String chatId) {
        return loveApp.doChat(message, chatId);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     */
    @GetMapping(value = "/love_app/chat/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> doChatWithLoveAppSSE(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId);
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     */
    @GetMapping(value = "/love_app/chat/server_sent_event")
    public Flux<ServerSentEvent<String>> doChatWithLoveAppServerSentEvent(String message, String chatId) {
        return loveApp.doChatByStream(message, chatId)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    /**
     * SSE 流式调用 AI 恋爱大师应用
     */
    @GetMapping(value = "/love_app/chat/sse_emitter")
    public SseEmitter doChatWithLoveAppServerSseEmitter(String message, String chatId) {
        SseEmitter sseEmitter = new SseEmitter(180000L);
        loveApp.doChatByStream(message, chatId)
                .subscribe(chunk -> {
                    try {
                        sseEmitter.send(chunk);
                    } catch (IOException e) {
                        sseEmitter.completeWithError(e);
                    }
                }, sseEmitter::completeWithError, sseEmitter::complete);
        return sseEmitter;
    }

    /**
     * 流式调用 Manus 超级智能体。
     * 前端需要持续传同一个 chatId，后端会按 chatId 保存和恢复消息历史。
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message, String chatId) {
        String conversationId = StringUtils.hasText(chatId) ? chatId : UUID.randomUUID().toString();

        try {
            Optional<String> directGraphAnswer = graphQuestionRouterService.route(message);
            if (directGraphAnswer.isPresent()) {
                return sendDirectManusAnswer(conversationId, message, directGraphAnswer.get());
            }
        } catch (Exception e) {
            return sendDirectManusAnswer(conversationId, message, "知识图谱预查询失败：" + e.getMessage());
        }

        HaoManus haoManus = new HaoManus(allTools, dashscopeChatModel);
        List<Message> history = manusMemoryMap.getOrDefault(conversationId, new ArrayList<>());
        haoManus.setMessageList(new ArrayList<>(history));
        haoManus.setMemorySaver(messages -> manusMemoryMap.put(conversationId, new ArrayList<>(messages)));

        return haoManus.runStream(message);
    }

    private SseEmitter sendDirectManusAnswer(String conversationId, String userMessage, String answer) {
        SseEmitter sseEmitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                List<Message> history = new ArrayList<>(manusMemoryMap.getOrDefault(conversationId, new ArrayList<>()));
                history.add(new UserMessage(userMessage));
                history.add(new AssistantMessage(answer));
                manusMemoryMap.put(conversationId, history);

                sseEmitter.send(answer);
                sseEmitter.complete();
            } catch (IOException e) {
                sseEmitter.completeWithError(e);
            }
        });

        return sseEmitter;
    }
}
