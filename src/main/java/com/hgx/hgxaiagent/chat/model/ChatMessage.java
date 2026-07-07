package com.hgx.hgxaiagent.chat.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话中的一条前端可见消息。
 */
@Data
public class ChatMessage {

    private String id;

    private String conversationId;

    private Long userId;

    /**
     * user 或 assistant。
     */
    private String role;

    /**
     * normal、step、error 等前端展示类型。
     */
    private String messageType;

    private String content;

    private LocalDateTime createdAt;
}
