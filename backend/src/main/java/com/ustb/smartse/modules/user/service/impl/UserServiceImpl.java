package com.ustb.smartse.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ustb.smartse.common.utils.TokenUtil;
import com.ustb.smartse.modules.user.dto.LoginResponse;
import com.ustb.smartse.modules.user.entity.User;
import com.ustb.smartse.modules.user.mapper.UserMapper;
import com.ustb.smartse.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public boolean register(User user) {
        try {
            // 检查用户名是否已存在
            QueryWrapper<User> query = new QueryWrapper<>();
            query.eq("username", user.getUsername());
            if (userMapper.selectOne(query) != null) {
                return false; // 用户名已存在
            }

            // 参数校验
            if (user.getUsername() == null || user.getPassword() == null) {
                return false;
            }

            // 加密密码
            user.setPassword(DigestUtils.md5DigestAsHex(user.getPassword().getBytes()));

            // 插入用户并打印结果
            int result = userMapper.insert(user);
            System.out.println("插入结果: " + result);

            return result>0;
        } catch (Exception e) {
            e.printStackTrace(); // 打印异常堆栈，便于调试
            return false; // 出现异常时返回false
        }
    }

    @Override
    public LoginResponse loginAndCache(String username, String password) {

        String new_password = DigestUtils.md5DigestAsHex(password.getBytes());
        User user = lambdaQuery()
                .eq(User::getUsername, username)
                .eq(User::getPassword, new_password)
                .one();

        if (user == null) return null;

        // 1. 生成 token
        String token = TokenUtil.generateToken();

        // 2. 将用户信息写入 Redis（30分钟过期）
        long expire = 30 * 60L;
        String key = "token:" + token;
        redisTemplate.opsForValue().set(key, user, expire, TimeUnit.SECONDS);

        // 3. 构造响应对象
        LoginResponse response = new LoginResponse();
        //response.setToken(token);
        //response.setExpireIn(expire);
        response.setUserId(user.getId().toString());
        response.setUsername(user.getUsername());
        //response.setEmail(user.getEmail());
        return response;
    }
}
