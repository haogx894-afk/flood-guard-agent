package com.hgx.hgxaiagent.knowledge.service;

import com.hgx.hgxaiagent.knowledge.model.KnowledgeDocument;
import com.hgx.hgxaiagent.knowledge.model.KnowledgeDocumentStatus;
import com.hgx.hgxaiagent.knowledge.repository.KnowledgeDocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class KnowledgeDocumentService {

    private static final String KNOWLEDGE_SOURCE_TYPE = "knowledge-base";

    private final KnowledgeDocumentRepository repository;

    private final VectorStore vectorStore;

    private final Path storageDir;

    public KnowledgeDocumentService(
            KnowledgeDocumentRepository repository,
            @Qualifier("pgVectorVectorStore") VectorStore vectorStore) {
        this.repository = repository;
        this.vectorStore = vectorStore;
        this.storageDir = Path.of(System.getProperty("user.dir"), "tmp", "knowledge-documents");
    }

    public List<KnowledgeDocument> listDocuments() {
        return repository.findAll();
    }

    public KnowledgeDocument uploadDocument(MultipartFile file, boolean replaceSameName) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        try {
            Files.createDirectories(storageDir);

            String originalFileName = sanitizeFileName(file.getOriginalFilename());
            String fileType = getFileType(originalFileName);
            validateFileType(fileType);

            byte[] fileBytes = file.getBytes();
            String fileHash = sha256Hex(fileBytes);

            KnowledgeDocument sameHashDocument = repository.findByFileHash(fileHash).orElse(null);
            if (sameHashDocument != null && sameHashDocument.getStatus() == KnowledgeDocumentStatus.COMPLETED) {
                return sameHashDocument;
            }

            if (replaceSameName) {
                for (KnowledgeDocument oldDocument : repository.findByFileName(originalFileName)) {
                    if (!oldDocument.getFileHash().equals(fileHash)) {
                        deleteDocument(oldDocument.getId());
                    }
                }
            }

            String documentId = UUID.randomUUID().toString();
            Path filePath = storageDir.resolve(documentId + "-" + originalFileName);
            Files.write(filePath, fileBytes);

            LocalDateTime now = LocalDateTime.now();
            KnowledgeDocument knowledgeDocument = KnowledgeDocument.builder()
                    .id(documentId)
                    .fileName(originalFileName)
                    .fileHash(fileHash)
                    .fileType(fileType)
                    .filePath(filePath.toString())
                    .status(KnowledgeDocumentStatus.UPLOADED)
                    .chunkCount(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            repository.insert(knowledgeDocument);

            ingestDocument(documentId);
            return repository.findById(documentId).orElse(knowledgeDocument);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败：" + e.getMessage(), e);
        }
    }

    public KnowledgeDocument rebuildDocument(String documentId) {
        KnowledgeDocument document = getDocumentOrThrow(documentId);
        deleteVectorsByDocumentId(documentId);
        ingestDocument(document.getId());
        return getDocumentOrThrow(documentId);
    }

    public void deleteDocument(String documentId) {
        KnowledgeDocument document = getDocumentOrThrow(documentId);
        deleteVectorsByDocumentId(documentId);
        repository.deleteById(documentId);

        try {
            Files.deleteIfExists(Path.of(document.getFilePath()));
        } catch (IOException e) {
            log.warn("删除本地知识库文件失败：{}", document.getFilePath(), e);
        }
    }

    private void ingestDocument(String documentId) {
        KnowledgeDocument document = getDocumentOrThrow(documentId);
        repository.updateProcessing(documentId);

        try {
            List<Document> rawDocuments = readDocument(document);
            List<Document> vectorDocuments = enrichMetadata(document, rawDocuments);

            if (vectorDocuments.isEmpty()) {
                throw new RuntimeException("文档没有解析出有效内容");
            }

            vectorStore.add(vectorDocuments);
            repository.updateCompleted(documentId, vectorDocuments.size());
        } catch (Exception e) {
            deleteVectorsByDocumentId(documentId);
            repository.updateFailed(documentId, e.getMessage());
            throw new RuntimeException("文档入库失败：" + e.getMessage(), e);
        }
    }

    private List<Document> readDocument(KnowledgeDocument document) {
        FileSystemResource resource = new FileSystemResource(document.getFilePath());
        String fileType = document.getFileType();

        if ("pdf".equals(fileType)) {
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                    .withPagesPerDocument(1)
                    .build();
            return new PagePdfDocumentReader(resource, config).get();
        }

        if ("md".equals(fileType) || "markdown".equals(fileType)) {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withHorizontalRuleCreateDocument(true)
                    .withIncludeCodeBlock(false)
                    .withIncludeBlockquote(false)
                    .build();
            return new MarkdownDocumentReader(resource, config).get();
        }

        return new TikaDocumentReader(resource).get();
    }

    private List<Document> enrichMetadata(KnowledgeDocument knowledgeDocument, List<Document> rawDocuments) {
        List<Document> documents = new ArrayList<>();

        for (int i = 0; i < rawDocuments.size(); i++) {
            Document rawDocument = rawDocuments.get(i);
            if (rawDocument.getText() == null || rawDocument.getText().isBlank()) {
                continue;
            }

            Map<String, Object> metadata = new HashMap<>(rawDocument.getMetadata());
            metadata.put("documentId", knowledgeDocument.getId());
            metadata.put("fileName", knowledgeDocument.getFileName());
            metadata.put("fileHash", knowledgeDocument.getFileHash());
            metadata.put("fileType", knowledgeDocument.getFileType());
            metadata.put("sourceType", KNOWLEDGE_SOURCE_TYPE);
            metadata.put("chunkIndex", i);

            documents.add(new Document(rawDocument.getText(), metadata));
        }

        return documents;
    }

    private void deleteVectorsByDocumentId(String documentId) {
        try {
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            vectorStore.delete(builder.eq("documentId", documentId).build());
        } catch (Exception e) {
            log.warn("删除文档向量失败，documentId={}", documentId, e);
        }
    }

    private KnowledgeDocument getDocumentOrThrow(String documentId) {
        return repository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在：" + documentId));
    }

    private String sanitizeFileName(String originalFileName) {
        String fileName = originalFileName == null ? "unknown" : Path.of(originalFileName).getFileName().toString();
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String getFileType(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private void validateFileType(String fileType) {
        if (!List.of("pdf", "md", "markdown", "doc", "docx").contains(fileType)) {
            throw new IllegalArgumentException("暂不支持该文件类型：" + fileType);
        }
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }
}
