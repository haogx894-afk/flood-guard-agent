package com.hgx.hgxaiagent.user.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求体。
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String user_account;

    private String user_password;
}
