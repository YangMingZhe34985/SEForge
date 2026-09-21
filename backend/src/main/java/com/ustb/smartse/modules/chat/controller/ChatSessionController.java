package com.ustb.smartse.modules.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ustb.smartse.common.Result;
import com.ustb.smartse.modules.chat.dto.*;
import com.ustb.smartse.modules.chat.entity.ChatSession;

import com.ustb.smartse.modules.chat.service.ChatSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatSessionController {

    private final ChatSessionService chatSessionService;

    /**
     * 获取用户的所有会话记录
     * 输入：
     * {
     *     "userId": <long>,      // 用户ID
     *     "page": <int>,         // 当前页码
     *     "size": <int>          // 每页记录数
     * }
     *
     * 输出：
     * {
     *     "code": <integer>,                             // 状态码，200表示操作成功
     *     "message": <string>,                           // 提示信息
     *     "data": [
     *         {
     *             "messageId": <long>,                   // 会话ID（消息ID）
     *             "senderType": <string>,                // 消息发送者类型（例如 "user" 或 "system"）
     *             "content": <string>,                   // 会话内容（例如会话标题）
     *             "contentType": <string>,               // 内容类型（例如 "text"）
     *             "createdAt": <datetime>                // 消息创建时间（ISO 8601 格式）
     *         }
     *         ...
     *     ]
     * }
     */
    @PostMapping("/sessions")
    public Result<List<ChatSessionListResponse>> getUserChatSessions(@RequestBody ChatSessionListRequest request) {
        Page<ChatSession> page = new Page<>(request.getPage(), request.getSize());

        List<ChatSession> sessions = chatSessionService.lambdaQuery()
                .eq(ChatSession::getUserId, request.getUserId())
                .orderByDesc(ChatSession::getCreatedAt)
                .page(page)
                .getRecords();

        List<ChatSessionListResponse> responseList = sessions.stream().map(session -> {
            ChatSessionListResponse response = new ChatSessionListResponse();
            response.setSessionId(session.getId());
            response.setSessionTitle(session.getSessionTitle());
            response.setStatus(session.getStatus());
            response.setCreatedAt(session.getCreatedAt());
            return response;
        }).collect(Collectors.toList());

        return Result.success(responseList);
    }

    /**
     * 创建一个新的会话
     * 输入：
     * {
     *     "userId": <long>,
     *     "sessionTitle": <string>
     * }
     *
     * 输出：
     * {
     *     "code": <integer>,
     *     "message": <string>,
     *     "data": {
     *         "sessionId": <long>,
     *         "message": <string>
     *     }
     * }
     */
    @PostMapping("/session/create")
    public Result<ChatSessionCreateResponse> createSession(@RequestBody ChatSessionCreateRequest request) {
        Long sessionId = chatSessionService.createSession(request.getUserId(), request.getSessionTitle());
        return Result.success(new ChatSessionCreateResponse(sessionId));
    }


    /**
     * 重命名一个会话
     * 输入：
     * {
     *     "sessionId": <long>,
     *     "sessionTitle": <string>
     * }
     *
     * 输出：
     * {
     *     "code": <integer>,
     *     "message": <string>,
     *     "data": {
     *         "sessionId": <long>,
     *         "newTitle": <string>,
     *         "message": <string>
     *     }
     * }
     */
    @PostMapping("/session/rename")
    public Result<ChatSessionRenameResponse> renameSession(@RequestBody ChatSessionRenameRequest request) {
        boolean success = chatSessionService.renameSession(request.getSessionId(), request.getSessionTitle());
        return success
                ? Result.success(new ChatSessionRenameResponse(request.getSessionId(), request.getSessionTitle()))
                : Result.error("会话不存在或更新失败");
    }

    /**
     * 删除会话及其对应的所有消息记录
     * 输入：
     * {
     *     "sessionId": <long>
     * }
     *
     * 输出：
     * {
     *     "code": <integer>,
     *     "message": <string>,
     *     "data": {
     *         "sessionId": <long>,
     *         "message": <string>
     *     }
     * }
     */
    @PostMapping("/session/delete")
    public Result<ChatSessionDeleteResponse> deleteSession(@RequestBody ChatSessionDeleteRequest request) {
        boolean success = chatSessionService.deleteSessionCascade(request.getSessionId());
        return success
                ? Result.success(new ChatSessionDeleteResponse(request.getSessionId()))
                : Result.error("删除失败，可能会话不存在");
    }

    /**
     * 加载某个具体会话的详情信息（包括该会话的全部对话消息）
     *
     * 输入：
     * {
     *     "sessionId": <long>  // 会话 ID
     * }
     *
     * 输出：
     * {
     *     "code": <integer>,     // 返回状态码（如 200 表示成功）
     *     "message": <string>,   // 返回信息（如 "操作成功"）
     *     "data": {
     *         "sessionId": <long>,             // 会话 ID
     *         "sessionTitle": <string>,        // 会话标题
     *         "status": <int>,                 // 状态（1进行中，0已结束）
     *         "createdAt": <datetime>,         // 创建时间
     *         "chatMessages": [                // 会话中的消息列表
     *             {
     *                 "role": <string>         //发送者类型，如 ai，user
     *                 "content": <string>,     // 消息内容
     *
     *             },
     *             ...
     *         ]
     *     }
     * }
     */

    @PostMapping("/session/load")
    public Result<ChatSessionLoadResponse> loadSession(@RequestBody ChatSessionLoadRequest request) {
        ChatSessionLoadResponse response = chatSessionService.loadSessionById(request.getSessionId());
        return response != null
                ? Result.success(response)
                : Result.error("会话不存在");
    }

}