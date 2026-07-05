package com.hgx.hgxaiagent.knowledge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    private String id;

    private String fileName;

    private String fileHash;

    private String fileType;

    private String filePath;

    private KnowledgeDocumentStatus status;

    private Integer chunkCount;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
