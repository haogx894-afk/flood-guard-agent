package com.hgx.hgxaiagent.user.service.impl;

import com.hgx.hgxaiagent.user.constant.UserConstant;
import com.hgx.hgxaiagent.user.model.User;
import com.hgx.hgxaiagent.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 用户服务实现。
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private static final String SALT = "hgx";

    private static final Pattern SPECIAL_CHARACTER_PATTERN = Pattern.compile("\\pP|\\pS|\\s+");

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<User> userRowMapper = new UserRowMapper();

    public UserServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        if (StringUtils.hasText(userAccount)) {
            userAccount = userAccount.trim();
        }

        if (!isValidAccountAndPassword(userAccount, userPassword) || !StringUtils.hasText(checkPassword)) {
            return -1;
        }
        if (checkPassword.length() < 8 || !Objects.equals(userPassword, checkPassword)) {
            return -1;
        }
        if (existsByAccount(userAccount)) {
            return -1;
        }

        int userRole = countUsers() == 0 ? UserConstant.ADMIN_ROLE : UserConstant.DEFAULT_ROLE;
        String encryptedPassword = encryptPassword(userPassword);

        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO app_user (
                            username, user_account, user_password, user_status, user_role, is_delete
                        )
                        VALUES (?, ?, ?, 0, ?, 0)
                        RETURNING id
                        """,
                Long.class,
                userAccount,
                userAccount,
                encryptedPassword,
                userRole
        );

        return id == null ? -1 : id;
    }

    @Override
    public User userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        if (StringUtils.hasText(userAccount)) {
            userAccount = userAccount.trim();
        }

        if (!isValidAccountAndPassword(userAccount, userPassword)) {
            return null;
        }

        String encryptedPassword = encryptPassword(userPassword);
        List<User> users = jdbcTemplate.query("""
                        SELECT *
                        FROM app_user
                        WHERE user_account = ?
                          AND user_password = ?
                          AND is_delete = 0
                        LIMIT 1
                        """,
                userRowMapper,
                userAccount,
                encryptedPassword
        );

        if (users.isEmpty()) {
            log.info("user login failed, userAccount cannot match userPassword");
            return null;
        }

        User safetyUser = getSafetyUser(users.get(0));
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, safetyUser);
        return safetyUser;
    }

    @Override
    public int userLogout(HttpServletRequest request) {
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return 1;
    }

    @Override
    public User getCurrentUser(HttpServletRequest request) {
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (!(userObj instanceof User loginUser)) {
            return null;
        }
        return loginUser;
    }

    @Override
    public List<User> searchUsers(String username) {
        String keyword = StringUtils.hasText(username) ? "%" + username.trim() + "%" : "%";
        return jdbcTemplate.query("""
                        SELECT *
                        FROM app_user
                        WHERE is_delete = 0
                          AND username LIKE ?
                        ORDER BY id DESC
                        LIMIT 200
                        """,
                userRowMapper,
                keyword
        ).stream().map(this::getSafetyUser).toList();
    }

    @Override
    public boolean deleteUser(Long id) {
        if (id == null || id <= 0) {
            return false;
        }
        int updatedRows = jdbcTemplate.update("""
                        UPDATE app_user
                        SET is_delete = 1,
                            update_time = now()
                        WHERE id = ?
                          AND is_delete = 0
                        """,
                id
        );
        return updatedRows > 0;
    }

    private boolean isValidAccountAndPassword(String userAccount, String userPassword) {
        if (!StringUtils.hasText(userAccount) || !StringUtils.hasText(userPassword)) {
            return false;
        }
        if (userAccount.length() < 4 || userPassword.length() < 8) {
            return false;
        }
        return !SPECIAL_CHARACTER_PATTERN.matcher(userAccount).find();
    }

    private boolean existsByAccount(String userAccount) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM app_user
                        WHERE user_account = ?
                          AND is_delete = 0
                        """,
                Long.class,
                userAccount
        );
        return count != null && count > 0;
    }

    private long countUsers() {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT count(*)
                        FROM app_user
                        WHERE is_delete = 0
                        """,
                Long.class
        );
        return count == null ? 0 : count;
    }

    private String encryptPassword(String userPassword) {
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes(StandardCharsets.UTF_8));
    }

    private User getSafetyUser(User user) {
        if (user == null) {
            return null;
        }
        User safetyUser = new User();
        safetyUser.setId(user.getId());
        safetyUser.setUsername(user.getUsername());
        safetyUser.setUserAccount(user.getUserAccount());
        safetyUser.setAvatarUrl(user.getAvatarUrl());
        safetyUser.setGender(user.getGender());
        safetyUser.setPhone(user.getPhone());
        safetyUser.setEmail(user.getEmail());
        safetyUser.setUserStatus(user.getUserStatus());
        safetyUser.setCreateTime(user.getCreateTime());
        safetyUser.setUpdateTime(user.getUpdateTime());
        safetyUser.setIsDelete(user.getIsDelete());
        safetyUser.setUserRole(user.getUserRole());
        return safetyUser;
    }

    private static class UserRowMapper implements RowMapper<User> {

        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setUserAccount(rs.getString("user_account"));
            user.setAvatarUrl(rs.getString("avatar_url"));
            user.setGender((Integer) rs.getObject("gender"));
            user.setUserPassword(rs.getString("user_password"));
            user.setPhone(rs.getString("phone"));
            user.setEmail(rs.getString("email"));
            user.setUserStatus((Integer) rs.getObject("user_status"));
            user.setCreateTime(toLocalDateTime(rs.getTimestamp("create_time")));
            user.setUpdateTime(toLocalDateTime(rs.getTimestamp("update_time")));
            user.setIsDelete((Integer) rs.getObject("is_delete"));
            user.setUserRole((Integer) rs.getObject("user_role"));
            return user;
        }

        private LocalDateTime toLocalDateTime(Timestamp timestamp) {
            return timestamp == null ? null : timestamp.toLocalDateTime();
        }
    }
}
