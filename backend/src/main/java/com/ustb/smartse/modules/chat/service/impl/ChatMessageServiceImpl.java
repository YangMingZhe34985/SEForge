package com.ustb.smartse.modules.chat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ustb.smartse.modules.chat.entity.ChatMessage;
import com.ustb.smartse.modules.chat.mapper.ChatMessageMapper;
import com.ustb.smartse.modules.chat.service.ChatMessageService;
import org.springframework.stereotype.Service;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {
}
