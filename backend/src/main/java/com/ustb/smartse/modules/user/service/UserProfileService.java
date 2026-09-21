package com.ustb.smartse.modules.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ustb.smartse.common.Result;
import com.ustb.smartse.modules.user.dto.UserProfileUpdateRequest;
import com.ustb.smartse.modules.user.entity.UserProfile;

public interface UserProfileService extends IService<UserProfile> {

    UserProfile getProfileByUserId(Long userId);
    Result<String> updateProfileWithCache(UserProfileUpdateRequest request);
}
