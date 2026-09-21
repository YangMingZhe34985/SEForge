package com.ustb.smartse.modules.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UserProfileUpdateRequest {
    private Long userId;
    private String nickname;
    private String avatarBase64;  // 仅用于新上传的base64头像
    private String avatarUrl;    // 用于现有的头像URL
    private String bio;
    private String school;
    private String major;
}
