package com.hgx.hgxaiagent.user.service;

import com.hgx.hgxaiagent.user.model.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

public interface UserService {

    long userRegister(String userAccount, String userPassword, String checkPassword);

    User userLogin(String userAccount, String userPassword, HttpServletRequest request);

    int userLogout(HttpServletRequest request);

    User getCurrentUser(HttpServletRequest request);

    List<User> searchUsers(String username);

    boolean deleteUser(Long id);
}
