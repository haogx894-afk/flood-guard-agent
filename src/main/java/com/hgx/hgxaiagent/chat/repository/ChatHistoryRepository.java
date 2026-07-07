package com.hgx.hgxaiagent.chat.repository;

import com.hgx.hgxaiagent.chat.model.ChatConversation;
import com.hgx.hgxaiagent.chat.model.ChatMessage;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户智能体对话历史的数据库访问层。
 */
@Repository
public class ChatHistoryRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ChatConversation> conversationRowMapper = (rs, rowNum) -> {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(rs.getString("id"));
        conversation.setUserId(rs.getLong("user_id"));
        conversation.setTitle(rs.getString("title"));
        conversation.setLastMessage(rs.getString("last_message"));
        conversation.setMessageCount(rs.getInt("message_count"));
        conversation.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        conversation.setUpdatedAt(toLocalDateTime(rs.getTimestamp("updated_at")));
        conversation.setExpiresAt(toLocalDateTime(rs.getTimestamp("expires_at")));
        return conversation;
    };

    private final RowMapper<ChatMessage> messageRowMapper = (rs, rowNum) -> {
        ChatMessage message = new ChatMessage();
        message.setId(rs.getString("id"));
        message.setConversationId(rs.getString("conversation_id"));
        message.setUserId(rs.getLong("user_id"));
        message.setRole(rs.getString("role"));
        message.setMessageType(rs.getString("message_type"));
        message.setContent(rs.getString("content"));
        message.setCreatedAt(toLocalDateTime(rs.getTimestamp("created_at")));
        return message;
    };

    public ChatHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS chat_conversation (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    last_message TEXT,
                    message_count INTEGER NOT NULL DEFAULT 0,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    expires_at TIMESTAMP NOT NULL,
                    is_delete INTEGER NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS chat_message (
                    id VARCHAR(64) PRIMARY KEY,
                    conversation_id VARCHAR(64) NOT NULL,
                    user_id BIGINT NOT NULL,
                    role VARCHAR(32) NOT NULL,
                    message_type VARCHAR(32) NOT NULL,
                    content TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_chat_conversation_user_updated
                ON chat_conversation(user_id, updated_at DESC)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_chat_conversation_expires
                ON chat_conversation(expires_at)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_chat_message_conversation_created
                ON chat_message(conversation_id, created_at ASC)
                """);
        deleteExpired(LocalDateTime.now());
    }

    public void insertConversation(ChatConversation conversation) {
        jdbcTemplate.update("""
                        INSERT INTO chat_conversation
                        (id, user_id, title, last_message, message_count, created_at, updated_at, expires_at, is_delete)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                        """,
                conversation.getId(),
                conversation.getUserId(),
                conversation.getTitle(),
                conversation.getLastMessage(),
                conversation.getMessageCount(),
                Timestamp.valueOf(conversation.getCreatedAt()),
                Timestamp.valueOf(conversation.getUpdatedAt()),
                Timestamp.valueOf(conversation.getExpiresAt())
        );
    }

    public Optional<ChatConversation> findConversationById(String conversationId) {
        List<ChatConversation> conversations = jdbcTemplate.query("""
                        SELECT *
                        FROM chat_conversation
                        WHERE id = ?
                          AND is_delete = 0
                          AND expires_at > now()
                        LIMIT 1
                        """,
                conversationRowMapper,
                conversationId
        );
        return conversations.stream().findFirst();
    }

    public Optional<ChatConversation> findConversationByIdAndUserId(String conversationId, Long userId) {
        List<ChatConversation> conversations = jdbcTemplate.query("""
                        SELECT *
                        FROM chat_conversation
                        WHERE id = ?
                          AND user_id = ?
                          AND is_delete = 0
                          AND expires_at > now()
                        LIMIT 1
                        """,
                conversationRowMapper,
                conversationId,
                userId
        );
        return conversations.stream().findFirst();
    }

    public List<ChatConversation> listConversations(Long userId) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM chat_conversation
                        WHERE user_id = ?
                          AND is_delete = 0
                          AND expires_at > now()
                        ORDER BY updated_at DESC
                        """,
                conversationRowMapper,
                userId
        );
    }

    public void insertMessage(ChatMessage message) {
        jdbcTemplate.update("""
                        INSERT INTO chat_message
                        (id, conversation_id, user_id, role, message_type, content, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                message.getId(),
                message.getConversationId(),
                message.getUserId(),
                message.getRole(),
                message.getMessageType(),
                message.getContent(),
                Timestamp.valueOf(message.getCreatedAt())
        );
    }

    public List<ChatMessage> listMessages(String conversationId, Long userId) {
        return jdbcTemplate.query("""
                        SELECT m.*
                        FROM chat_message m
                        JOIN chat_conversation c ON c.id = m.conversation_id
                        WHERE m.conversation_id = ?
                          AND m.user_id = ?
                          AND c.user_id = ?
                          AND c.is_delete = 0
                          AND c.expires_at > now()
                        ORDER BY m.created_at ASC
                        """,
                messageRowMapper,
                conversationId,
                userId,
                userId
        );
    }

    public void updateConversationAfterMessage(String conversationId, Long userId, String title, String lastMessage) {
        if (StringUtils.hasText(title)) {
            jdbcTemplate.update("""
                            UPDATE chat_conversation
                            SET title = ?,
                                last_message = ?,
                                message_count = message_count + 1,
                                updated_at = ?
                            WHERE id = ?
                              AND user_id = ?
                              AND is_delete = 0
                            """,
                    title,
                    lastMessage,
                    Timestamp.valueOf(LocalDateTime.now()),
                    conversationId,
                    userId
            );
            return;
        }

        jdbcTemplate.update("""
                        UPDATE chat_conversation
                        SET last_message = ?,
                            message_count = message_count + 1,
                            updated_at = ?
                        WHERE id = ?
                          AND user_id = ?
                          AND is_delete = 0
                        """,
                lastMessage,
                Timestamp.valueOf(LocalDateTime.now()),
                conversationId,
                userId
        );
    }

    public boolean deleteConversation(String conversationId, Long userId) {
        int updatedRows = jdbcTemplate.update("""
                        UPDATE chat_conversation
                        SET is_delete = 1,
                            updated_at = ?
                        WHERE id = ?
                          AND user_id = ?
                          AND is_delete = 0
                        """,
                Timestamp.valueOf(LocalDateTime.now()),
                conversationId,
                userId
        );
        if (updatedRows > 0) {
            jdbcTemplate.update("""
                            DELETE FROM chat_message
                            WHERE conversation_id = ?
                              AND user_id = ?
                            """,
                    conversationId,
                    userId
            );
        }
        return updatedRows > 0;
    }

    public int deleteExpired(LocalDateTime now) {
        List<String> expiredConversationIds = jdbcTemplate.queryForList("""
                        SELECT id
                        FROM chat_conversation
                        WHERE expires_at <= ?
                        """,
                String.class,
                Timestamp.valueOf(now)
        );
        if (expiredConversationIds.isEmpty()) {
            return 0;
        }

        for (String conversationId : expiredConversationIds) {
            jdbcTemplate.update("DELETE FROM chat_message WHERE conversation_id = ?", conversationId);
        }
        return jdbcTemplate.update("DELETE FROM chat_conversation WHERE expires_at <= ?", Timestamp.valueOf(now));
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
