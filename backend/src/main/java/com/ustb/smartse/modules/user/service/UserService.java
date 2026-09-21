package com.ustb.smartse.modules.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ustb.smartse.modules.user.dto.LoginResponse;
import com.ustb.smartse.modules.user.entity.User;

public interface UserService extends IService<User> {
    boolean register(User user);
    LoginResponse loginAndCache(String username, String password);
}
