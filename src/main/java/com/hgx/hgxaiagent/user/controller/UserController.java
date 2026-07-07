package com.hgx.hgxaiagent.user.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.hgx.hgxaiagent.user.constant.UserConstant;
import com.hgx.hgxaiagent.user.model.User;
import com.hgx.hgxaiagent.user.model.request.UserLoginRequest;
import com.hgx.hgxaiagent.user.model.request.UserRegisterRequest;
import com.hgx.hgxaiagent.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户接口。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public Long register(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            return null;
        }
        String userAccount = userRegisterRequest.getUser_account();
        String userPassword = userRegisterRequest.getUser_password();
        String checkPassword = userRegisterRequest.getCheckpassword();
        if (!StringUtils.hasText(userAccount)
                || !StringUtils.hasText(userPassword)
                || !StringUtils.hasText(checkPassword)) {
            return null;
        }
        return userService.userRegister(userAccount, userPassword, checkPassword);
    }

    @PostMapping("/login")
    public User login(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        if (userLoginRequest == null) {
            return null;
        }
        String userAccount = userLoginRequest.getUser_account();
        String userPassword = userLoginRequest.getUser_password();
        if (!StringUtils.hasText(userAccount) || !StringUtils.hasText(userPassword)) {
            return null;
        }
        return userService.userLogin(userAccount, userPassword, request);
    }

    @GetMapping("/current")
    public User getCurrentUser(HttpServletRequest request) {
        return userService.getCurrentUser(request);
    }

    @GetMapping("/search")
    public List<User> searchUsers(@RequestParam(required = false) String username, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return new ArrayList<>();
        }
        return userService.searchUsers(username);
    }

    @PostMapping("/delete")
    public Boolean deleteUser(@RequestBody JsonNode body, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return false;
        }

        Long id = parseDeleteUserId(body);
        if (id == null) {
            return false;
        }
        return userService.deleteUser(id);
    }

    @PostMapping("/logout")
    public Integer logout(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return userService.userLogout(request);
    }

    private boolean isAdmin(HttpServletRequest request) {
        User user = userService.getCurrentUser(request);
        return user != null && user.getUserRole() != null && user.getUserRole() == UserConstant.ADMIN_ROLE;
    }

    private Long parseDeleteUserId(JsonNode body) {
        if (body == null || body.isNull()) {
            return null;
        }
        if (body.isNumber()) {
            return body.asLong();
        }
        if (body.has("id") && body.get("id").isNumber()) {
            return body.get("id").asLong();
        }
        return null;
    }
}
