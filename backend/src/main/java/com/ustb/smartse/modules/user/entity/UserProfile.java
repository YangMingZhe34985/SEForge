package com.ustb.smartse.modules.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.sql.Timestamp;

@Data
@TableName("user_profile")
public class UserProfile {

    @TableId
    private Long id;

    private Long userId;

    private String nickname;

    private String avatarUrl;

    private String bio;

    private String school;

    private String major;

    private Timestamp createdAt;

    private Timestamp updatedAt;
}
