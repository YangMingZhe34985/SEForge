package com.ustb.smartse.modules.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.sql.Timestamp;

@Data
@TableName("user")
public class User {

    @TableId
    private Long id;

    private String username;

    private String password;

    private String email;

    private String phone;

    private Integer status;

    private Timestamp createdAt;

    private Timestamp updatedAt;
}
