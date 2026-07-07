package com.hgx.hgxaiagent.chat.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户与智能体的一次对话会话。
 */
@Data
public class ChatConversation {

    private String id;

    private Long userId;

    private String title;

    private String lastMessage;

    private Integer messageCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime expiresAt;
}
