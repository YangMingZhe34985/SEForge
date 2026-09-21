package com.ustb.smartse.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ustb.smartse.modules.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
