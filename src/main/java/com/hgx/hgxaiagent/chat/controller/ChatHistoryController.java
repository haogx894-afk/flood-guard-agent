package com.hgx.hgxaiagent.chat.controller;

import com.hgx.hgxaiagent.chat.model.ChatConversation;
import com.hgx.hgxaiagent.chat.model.ChatMessage;
import com.hgx.hgxaiagent.chat.model.request.ChatConversationCreateRequest;
import com.hgx.hgxaiagent.chat.service.ChatHistoryService;
import com.hgx.hgxaiagent.user.model.User;
import com.hgx.hgxaiagent.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 用户智能体对话历史接口。
 */
@RestController
@RequestMapping("/chat")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    private final UserService userService;

    public ChatHistoryController(ChatHistoryService chatHistoryService, UserService userService) {
        this.chatHistoryService = chatHistoryService;
        this.userService = userService;
    }

    @GetMapping("/conversations")
    public List<ChatConversation> listConversations(HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        return chatHistoryService.listConversations(loginUser.getId());
    }

    @PostMapping("/conversations")
    public ChatConversation createConversation(@RequestBody(required = false) ChatConversationCreateRequest createRequest,
                                               HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        String title = createRequest == null ? null : createRequest.getTitle();
        return chatHistoryService.createConversation(loginUser.getId(), title);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessage> listMessages(@PathVariable String conversationId, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        assertConversationId(conversationId);
        try {
            return chatHistoryService.listMessages(loginUser.getId(), conversationId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Boolean deleteConversation(@PathVariable String conversationId, HttpServletRequest request) {
        User loginUser = getLoginUser(request);
        assertConversationId(conversationId);
        try {
            return chatHistoryService.deleteConversation(loginUser.getId(), conversationId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private User getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getCurrentUser(request);
        if (loginUser == null || loginUser.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return loginUser;
    }

    private void assertConversationId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对话 id 不能为空");
        }
    }
}
