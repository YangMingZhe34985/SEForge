package com.ustb.smartse.modules.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ustb.smartse.common.Result;
import com.ustb.smartse.modules.user.dto.UserProfileUpdateRequest;
import com.ustb.smartse.modules.user.dto.UserRegisterRequest;
import com.ustb.smartse.modules.user.entity.User;
import com.ustb.smartse.modules.user.dto.LoginRequest;
import com.ustb.smartse.modules.user.dto.LoginResponse;
import com.ustb.smartse.modules.user.entity.UserProfile;
import com.ustb.smartse.modules.user.service.UserProfileService;
import com.ustb.smartse.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserProfileService userProfileService;

    /**
     * 注册接口
     */
    @PostMapping("/register")
    public Result<String> register(@RequestBody UserRegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(request.getPassword());

        boolean success = userService.register(user);
        if (success) {
            // 查询注册后写入数据库的 User 对象（假设用户名是唯一的）
            User createdUser = userService.getOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, user.getUsername())
            );
            if (createdUser == null) {
                return Result.error("Failed to retrieve created user.");
            }

            // 创建默认的 UserProfile
            UserProfile profile = new UserProfile();
            profile.setUserId(createdUser.getId());
            profile.setNickname("新用户"); // 默认昵称
            profile.setAvatarUrl(null);  // 可以设置默认头像地址
            profile.setBio("");
            profile.setSchool("");
            profile.setMajor("");
            userProfileService.save(profile); // 保存用户资料

            return Result.success("Successfully registered!");
        }

        return Result.error("User already exists!");
    }

    /**
     * 登录接口
     * 输入：
     * {
     *     "username": "test001",
     *     "password": "123456"
     * }
     * 输出：
     * {
     *     "code": 200,
     *     "message": "操作成功",
     *     "data": {
     *         "userId": 1,                // 用户 ID
     *         "username": "test001",     // 用户名
     *         "email": "test001@..."      // 邮箱（可选）
     *     }
     * }
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest loginRequest) {
        LoginResponse res = userService.loginAndCache(loginRequest.getUsername(), loginRequest.getPassword());
        if (res != null) {
            return Result.success(res);
        } else {
            return Result.error("Invalid username or password!");
        }
    }

    /**
     * 加载个人信息接口
     * 输入：用户 ID（路径参数）
     * 输出：
     * {
     *     "code": 200,
     *     "message": "操作成功",
     *     "data": {
     *         "id": 1,
     *         "userId": 1,
     *         "nickname": "张三",
     *         "avatarUrl": "http://xxx.png",
     *         "bio": "男",
     *         "school": "北京科技大学",
     *         "major": "计算机科学与技术",
     *         "createdAt": "2025-01-01T00:00:00",
     *         "updatedAt": "2025-05-01T00:00:00"
     *     }
     * }
     */
    @GetMapping("/profile/{userId}")
    public Result<UserProfile> getProfile(@PathVariable Long userId) {
        UserProfile profile = userProfileService.getProfileByUserId(userId);
        if (profile != null) {
            return Result.success(profile);
        } else {
            return Result.error("User profile not found.");
        }
    }

    /**
     * 修改个人信息接口
     *
     * 请求方式：PUT
     * 请求路径：/api/user/profile
     * 请求体参数：包含 userId 在内的 UserProfile 对象
     *
     * 示例输入：
     * {
     *   "userId": 1,
     *   "nickname": "李四",
     *   "avatarUrl": "http://example.com/avatar.jpg",
     *   "bio": "热爱编程",
     *   "school": "北京科技大学",
     *   "major": "人工智能"
     * }
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "message": "Profile updated and cache refreshed.",
     *   "data": null
     * }
     *
     * 功能说明：更新数据库中的用户个人信息，并同步更新 Redis 缓存。
     */
    @PutMapping("/profile")
    public Result<String> updateProfile(@RequestBody UserProfileUpdateRequest request) {
        return userProfileService.updateProfileWithCache(request);
    }


}
