package com.ustb.smartse.modules.user.dto;

import lombok.Data;

@Data
public class LoginResponse {
    //private String token;      // 登录令牌
    //private Long expireIn;     // 有效期（秒）
    private String userId;       // 用户ID
    private String username;   // 用户名
    //private String email;      // 邮箱（可选）
}
