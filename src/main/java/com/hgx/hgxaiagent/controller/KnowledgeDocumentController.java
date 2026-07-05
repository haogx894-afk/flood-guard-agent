package com.hgx.hgxaiagent.controller;

import com.hgx.hgxaiagent.knowledge.model.KnowledgeDocument;
import com.hgx.hgxaiagent.knowledge.service.KnowledgeDocumentService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/knowledge/documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    public KnowledgeDocumentController(KnowledgeDocumentService knowledgeDocumentService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
    }

    @GetMapping
    public List<KnowledgeDocument> listDocuments() {
        return knowledgeDocumentService.listDocuments();
    }

    @PostMapping("/upload")
    public KnowledgeDocument uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean replaceSameName) {
        return knowledgeDocumentService.uploadDocument(file, replaceSameName);
    }

    @PostMapping("/{documentId}/rebuild")
    public KnowledgeDocument rebuildDocument(@PathVariable String documentId) {
        return knowledgeDocumentService.rebuildDocument(documentId);
    }

    @DeleteMapping("/{documentId}")
    public void deleteDocument(@PathVariable String documentId) {
        knowledgeDocumentService.deleteDocument(documentId);
    }
}
