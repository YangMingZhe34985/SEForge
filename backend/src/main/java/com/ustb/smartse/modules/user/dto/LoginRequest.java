package com.ustb.smartse.modules.user.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
