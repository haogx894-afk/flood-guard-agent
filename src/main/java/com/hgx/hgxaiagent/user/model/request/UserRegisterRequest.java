package com.hgx.hgxaiagent.user.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册请求体。
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String user_account;

    private String user_password;

    private String checkpassword;
}
