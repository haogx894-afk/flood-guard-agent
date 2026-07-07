package com.hgx.hgxaiagent.controller;

import com.hgx.hgxaiagent.knowledge.model.KnowledgeDocument;
import com.hgx.hgxaiagent.knowledge.service.KnowledgeDocumentService;
import com.hgx.hgxaiagent.user.constant.UserConstant;
import com.hgx.hgxaiagent.user.model.User;
import com.hgx.hgxaiagent.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/knowledge/documents")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    private final UserService userService;

    public KnowledgeDocumentController(KnowledgeDocumentService knowledgeDocumentService, UserService userService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.userService = userService;
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
    public void deleteDocument(@PathVariable String documentId, HttpServletRequest request) {
        assertAdmin(request);
        knowledgeDocumentService.deleteDocument(documentId);
    }

    private void assertAdmin(HttpServletRequest request) {
        User loginUser = userService.getCurrentUser(request);
        if (loginUser == null || loginUser.getUserRole() == null || loginUser.getUserRole() != UserConstant.ADMIN_ROLE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有管理员可以删除知识库文档");
        }
    }
}
