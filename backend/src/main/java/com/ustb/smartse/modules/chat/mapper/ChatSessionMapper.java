package com.ustb.smartse.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ustb.smartse.modules.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
