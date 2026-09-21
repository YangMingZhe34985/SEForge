package com.ustb.smartse.modules.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ustb.smartse.common.Result;
import com.ustb.smartse.modules.user.dto.UserProfileUpdateRequest;
import com.ustb.smartse.modules.user.entity.UserProfile;
import com.ustb.smartse.modules.user.mapper.UserProfileMapper;
import com.ustb.smartse.modules.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl extends ServiceImpl<UserProfileMapper, UserProfile> implements UserProfileService {

    private final RedisTemplate<String, Object> redisTemplate;
    @Value("${avatar.base-dir}")
    private String avatarBaseDir;

    @Value("${avatar.access-url-prefix}")
    private String avatarUrlPrefix;

    @Override
    public UserProfile getProfileByUserId(Long userId) {
        String key = "user:profile:" + userId;

        // 1. 尝试从 Redis 获取缓存
        UserProfile cached = (UserProfile) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        // 2. 缓存未命中，查数据库
        UserProfile profile = lambdaQuery()
                .eq(UserProfile::getUserId, userId)
                .one();

        // 3. 写入 Redis，设置过期时间（10 分钟）
        if (profile != null) {
            redisTemplate.opsForValue().set(key, profile, 10, TimeUnit.MINUTES);
        }

        return profile;
    }

    @Override
    public Result<String> updateProfileWithCache(UserProfileUpdateRequest request) {
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(request.getUserId());
        userProfile.setNickname(request.getNickname());
        userProfile.setBio(request.getBio());
        userProfile.setSchool(request.getSchool());
        userProfile.setMajor(request.getMajor());

        // 修改头像处理逻辑
        if (request.getAvatarBase64() != null && !request.getAvatarBase64().isEmpty()) {
            try {
                String fileName = "avatar_" + request.getUserId() + "_" + System.currentTimeMillis() + ".png";
                Path savePath = Paths.get(avatarBaseDir, fileName);
                Files.createDirectories(savePath.getParent());

                byte[] imageBytes = Base64.getDecoder().decode(
                        request.getAvatarBase64().replaceFirst("^data:image/\\w+;base64,", "")
                );
                Files.write(savePath, imageBytes);
                userProfile.setAvatarUrl(avatarUrlPrefix + fileName);
            } catch (IOException e) {
                return Result.error("头像上传失败：" + e.getMessage());
            }
        } else if (request.getAvatarUrl() != null) {
            // 保留现有头像URL
            userProfile.setAvatarUrl(request.getAvatarUrl());
        }

        // 更新数据库
        boolean updated = update(userProfile, new QueryWrapper<UserProfile>().eq("user_id", request.getUserId()));
        if (updated) {
            // 同步缓存
            String key = "user:profile:" + request.getUserId();
            redisTemplate.opsForValue().set(key, userProfile, 10, TimeUnit.MINUTES);
            return Result.success("Profile updated and cache refreshed.");
        } else {
            return Result.error("Failed to update profile.");
        }
    }


}
