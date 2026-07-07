package com.hgx.hgxaiagent.chat.service;

import com.hgx.hgxaiagent.chat.model.ChatConversation;
import com.hgx.hgxaiagent.chat.model.ChatMessage;
import com.hgx.hgxaiagent.chat.repository.ChatHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 用户与智能体对话历史服务。
 */
@Service
@Slf4j
public class ChatHistoryService {

    private static final int RETENTION_DAYS = 30;

    private static final String DEFAULT_TITLE = "新对话";

    private final ChatHistoryRepository chatHistoryRepository;

    public ChatHistoryService(ChatHistoryRepository chatHistoryRepository) {
        this.chatHistoryRepository = chatHistoryRepository;
    }

    public ChatConversation createConversation(Long userId, String title) {
        return createConversation(userId, UUID.randomUUID().toString(), title);
    }

    public ChatConversation ensureConversation(Long userId, String conversationId, String firstMessage) {
        if (StringUtils.hasText(conversationId)) {
            String safeConversationId = conversationId.trim();
            var existingConversation = chatHistoryRepository.findConversationById(safeConversationId);
            if (existingConversation.isPresent()) {
                ChatConversation conversation = existingConversation.get();
                if (!conversation.getUserId().equals(userId)) {
                    throw new IllegalArgumentException("无权访问该对话");
                }
                return conversation;
            }
            return createConversation(userId, safeConversationId, buildTitle(firstMessage));
        }

        return createConversation(userId, buildTitle(firstMessage));
    }

    public List<ChatConversation> listConversations(Long userId) {
        deleteExpiredConversations();
        return chatHistoryRepository.listConversations(userId);
    }

    public List<ChatMessage> listMessages(Long userId, String conversationId) {
        assertConversationOwner(userId, conversationId);
        return chatHistoryRepository.listMessages(conversationId, userId);
    }

    public boolean deleteConversation(Long userId, String conversationId) {
        assertConversationOwner(userId, conversationId);
        return chatHistoryRepository.deleteConversation(conversationId, userId);
    }

    public ChatMessage addUserMessage(Long userId, String conversationId, String content) {
        return addMessage(userId, conversationId, "user", "normal", content);
    }

    public ChatMessage addAssistantMessage(Long userId, String conversationId, String content) {
        String messageType = StringUtils.hasText(content) && content.trim().startsWith("Step ") ? "step" : "normal";
        if (StringUtils.hasText(content) && content.trim().startsWith("执行错误")) {
            messageType = "error";
        }
        return addMessage(userId, conversationId, "assistant", messageType, content);
    }

    public List<Message> loadSpringAiMessages(Long userId, String conversationId) {
        assertConversationOwner(userId, conversationId);
        List<ChatMessage> historyMessages = chatHistoryRepository.listMessages(conversationId, userId);
        List<Message> messages = new ArrayList<>();
        for (ChatMessage historyMessage : historyMessages) {
            if ("user".equals(historyMessage.getRole())) {
                messages.add(new UserMessage(historyMessage.getContent()));
            } else if ("assistant".equals(historyMessage.getRole())) {
                messages.add(new AssistantMessage(historyMessage.getContent()));
            }
        }
        return messages;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void deleteExpiredConversations() {
        int deletedRows = chatHistoryRepository.deleteExpired(LocalDateTime.now());
        if (deletedRows > 0) {
            log.info("自动清理过期对话 {} 条", deletedRows);
        }
    }

    private ChatConversation createConversation(Long userId, String conversationId, String title) {
        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = new ChatConversation();
        conversation.setId(conversationId);
        conversation.setUserId(userId);
        conversation.setTitle(StringUtils.hasText(title) ? title.trim() : DEFAULT_TITLE);
        conversation.setLastMessage("");
        conversation.setMessageCount(0);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversation.setExpiresAt(now.plusDays(RETENTION_DAYS));
        chatHistoryRepository.insertConversation(conversation);
        return conversation;
    }

    private ChatMessage addMessage(Long userId, String conversationId, String role, String messageType, String content) {
        assertConversationOwner(userId, conversationId);
        String safeContent = StringUtils.hasText(content) ? content.trim() : "";
        ChatMessage message = new ChatMessage();
        message.setId(UUID.randomUUID().toString());
        message.setConversationId(conversationId);
        message.setUserId(userId);
        message.setRole(role);
        message.setMessageType(messageType);
        message.setContent(safeContent);
        message.setCreatedAt(LocalDateTime.now());
        chatHistoryRepository.insertMessage(message);

        String title = null;
        if ("user".equals(role)) {
            ChatConversation conversation = chatHistoryRepository.findConversationByIdAndUserId(conversationId, userId)
                    .orElse(null);
            if (conversation != null
                    && (conversation.getMessageCount() == null || conversation.getMessageCount() == 0
                    || DEFAULT_TITLE.equals(conversation.getTitle()))) {
                title = buildTitle(safeContent);
            }
        }
        chatHistoryRepository.updateConversationAfterMessage(conversationId, userId, title, buildLastMessage(role, safeContent));
        return message;
    }

    private void assertConversationOwner(Long userId, String conversationId) {
        if (userId == null || !StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("对话参数不完整");
        }
        if (chatHistoryRepository.findConversationByIdAndUserId(conversationId.trim(), userId).isEmpty()) {
            throw new IllegalArgumentException("对话不存在或无权访问");
        }
    }

    private String buildTitle(String message) {
        if (!StringUtils.hasText(message)) {
            return DEFAULT_TITLE;
        }
        String text = message.trim().replaceAll("\\s+", " ");
        return text.length() <= 24 ? text : text.substring(0, 24) + "...";
    }

    private String buildLastMessage(String role, String content) {
        String prefix = "user".equals(role) ? "我：" : "智能体：";
        String text = StringUtils.hasText(content) ? content.replaceAll("\\s+", " ").trim() : "";
        if (text.length() > 80) {
            text = text.substring(0, 80) + "...";
        }
        return prefix + text;
    }
}
