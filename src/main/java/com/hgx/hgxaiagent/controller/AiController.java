package com.hgx.hgxaiagent.controller;

import com.hgx.hgxaiagent.agent.HaoManus;
import com.hgx.hgxaiagent.app.LoveApp;
import com.hgx.hgxaiagent.chat.model.ChatConversation;
import com.hgx.hgxaiagent.chat.service.ChatHistoryService;
import com.hgx.hgxaiagent.knowledgegraph.service.GraphQuestionRouterService;
import com.hgx.hgxaiagent.user.model.User;
import com.hgx.hgxaiagent.user.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private UserService userService;

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
     * chatId 对应一条持久化对话，后端会按当前登录用户隔离历史记录。
     */
    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message, String chatId, HttpServletRequest request) {
        User loginUser = userService.getCurrentUser(request);
        if (loginUser == null || loginUser.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        if (!StringUtils.hasText(message)) {
            return sendDirectManusAnswer(loginUser.getId(), null, "请输入问题后再发送。");
        }

        ChatConversation conversation;
        try {
            conversation = chatHistoryService.ensureConversation(loginUser.getId(), chatId, message);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        String conversationId = conversation.getId();
        List<Message> history = chatHistoryService.loadSpringAiMessages(loginUser.getId(), conversationId);
        chatHistoryService.addUserMessage(loginUser.getId(), conversationId, message);

        try {
            Optional<String> directGraphAnswer = graphQuestionRouterService.route(message);
            if (directGraphAnswer.isPresent()) {
                return sendDirectManusAnswer(loginUser.getId(), conversationId, directGraphAnswer.get());
            }
        } catch (Exception e) {
            return sendDirectManusAnswer(loginUser.getId(), conversationId, "知识图谱预查询失败：" + e.getMessage());
        }

        HaoManus haoManus = new HaoManus(allTools, dashscopeChatModel);
        haoManus.setMessageList(history);
        haoManus.setStreamOutputSaver(output ->
                chatHistoryService.addAssistantMessage(loginUser.getId(), conversationId, output)
        );

        return haoManus.runStream(message);
    }

    private SseEmitter sendDirectManusAnswer(Long userId, String conversationId, String answer) {
        SseEmitter sseEmitter = new SseEmitter(300000L);

        CompletableFuture.runAsync(() -> {
            try {
                if (userId != null && StringUtils.hasText(conversationId)) {
                    chatHistoryService.addAssistantMessage(userId, conversationId, answer);
                }

                sseEmitter.send(answer);
                sseEmitter.complete();
            } catch (IOException e) {
                sseEmitter.completeWithError(e);
            }
        });

        return sseEmitter;
    }
}
