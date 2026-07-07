package com.hgx.hgxaiagent.user.model.request;

import lombok.Data;

import java.io.Serializable;

/**
 * 删除用户请求体。
 */
@Data
public class UserDeleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
}
