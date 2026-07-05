package com.hgx.hgxaiagent.knowledge.repository;

import com.hgx.hgxaiagent.knowledge.model.KnowledgeDocument;
import com.hgx.hgxaiagent.knowledge.model.KnowledgeDocumentStatus;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class KnowledgeDocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<KnowledgeDocument> rowMapper = (rs, rowNum) -> KnowledgeDocument.builder()
            .id(rs.getString("id"))
            .fileName(rs.getString("file_name"))
            .fileHash(rs.getString("file_hash"))
            .fileType(rs.getString("file_type"))
            .filePath(rs.getString("file_path"))
            .status(KnowledgeDocumentStatus.valueOf(rs.getString("status")))
            .chunkCount(rs.getInt("chunk_count"))
            .errorMessage(rs.getString("error_message"))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
            .build();

    public KnowledgeDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS knowledge_document (
                    id VARCHAR(64) PRIMARY KEY,
                    file_name VARCHAR(512) NOT NULL,
                    file_hash VARCHAR(128) NOT NULL,
                    file_type VARCHAR(32) NOT NULL,
                    file_path TEXT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    chunk_count INTEGER NOT NULL DEFAULT 0,
                    error_message TEXT,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_knowledge_document_file_hash
                ON knowledge_document(file_hash)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_knowledge_document_file_name
                ON knowledge_document(file_name)
                """);
    }

    public void insert(KnowledgeDocument document) {
        jdbcTemplate.update("""
                        INSERT INTO knowledge_document
                        (id, file_name, file_hash, file_type, file_path, status, chunk_count, error_message, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                document.getId(),
                document.getFileName(),
                document.getFileHash(),
                document.getFileType(),
                document.getFilePath(),
                document.getStatus().name(),
                document.getChunkCount(),
                document.getErrorMessage(),
                Timestamp.valueOf(document.getCreatedAt()),
                Timestamp.valueOf(document.getUpdatedAt()));
    }

    public List<KnowledgeDocument> findAll() {
        return jdbcTemplate.query("""
                SELECT * FROM knowledge_document
                ORDER BY updated_at DESC
                """, rowMapper);
    }

    public Optional<KnowledgeDocument> findById(String id) {
        List<KnowledgeDocument> documents = jdbcTemplate.query("""
                SELECT * FROM knowledge_document
                WHERE id = ?
                """, rowMapper, id);
        return documents.stream().findFirst();
    }

    public Optional<KnowledgeDocument> findByFileHash(String fileHash) {
        List<KnowledgeDocument> documents = jdbcTemplate.query("""
                SELECT * FROM knowledge_document
                WHERE file_hash = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """, rowMapper, fileHash);
        return documents.stream().findFirst();
    }

    public List<KnowledgeDocument> findByFileName(String fileName) {
        return jdbcTemplate.query("""
                SELECT * FROM knowledge_document
                WHERE file_name = ?
                ORDER BY updated_at DESC
                """, rowMapper, fileName);
    }

    public void updateProcessing(String id) {
        updateStatus(id, KnowledgeDocumentStatus.PROCESSING, 0, null);
    }

    public void updateCompleted(String id, int chunkCount) {
        updateStatus(id, KnowledgeDocumentStatus.COMPLETED, chunkCount, null);
    }

    public void updateFailed(String id, String errorMessage) {
        updateStatus(id, KnowledgeDocumentStatus.FAILED, 0, errorMessage);
    }

    private void updateStatus(String id, KnowledgeDocumentStatus status, int chunkCount, String errorMessage) {
        jdbcTemplate.update("""
                        UPDATE knowledge_document
                        SET status = ?, chunk_count = ?, error_message = ?, updated_at = ?
                        WHERE id = ?
                        """,
                status.name(),
                chunkCount,
                errorMessage,
                Timestamp.valueOf(LocalDateTime.now()),
                id);
    }

    public void deleteById(String id) {
        jdbcTemplate.update("DELETE FROM knowledge_document WHERE id = ?", id);
    }
}
