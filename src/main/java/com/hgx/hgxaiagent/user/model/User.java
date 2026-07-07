package com.hgx.hgxaiagent.user.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体。
 */
@Data
public class User {

    private Long id;

    private String username;

    private String userAccount;

    private String avatarUrl;

    private Integer gender;

    private String userPassword;

    private String phone;

    private String email;

    private Integer userStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer isDelete;

    /**
     * 用户角色：0 普通用户，1 管理员。
     */
    private Integer userRole;
}
