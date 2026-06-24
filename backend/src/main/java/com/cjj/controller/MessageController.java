package com.cjj.controller;

import com.cjj.dto.Result;
import com.cjj.service.impl.MessageServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    @Resource
    private MessageServiceImpl messageService;

    /**
     * 发送消息
     * POST /api/messages  { "toUserId": 2, "content": "你好" }
     */
    @PostMapping
    public Result sendMessage(@RequestBody Map<String, Object> body) {
        Object toObj = body.get("toUserId");
        Object contentObj = body.get("content");
        if (toObj == null || contentObj == null) {
            return Result.fail("参数不完整");
        }
        Long toUserId = Long.valueOf(toObj.toString());
        String content = contentObj.toString().trim();
        return messageService.sendMessage(toUserId, content);
    }

    /**
     * 会话列表
     * GET /api/messages/conversations
     */
    @GetMapping("/conversations")
    public Result getConversations() {
        return messageService.getConversations();
    }

    /**
     * 与某人的聊天记录
     * GET /api/messages/with/{userId}
     */
    @GetMapping("/with/{userId}")
    public Result getMessagesWith(@PathVariable Long userId) {
        return messageService.getMessagesWith(userId);
    }

    /**
     * 长轮询等待新消息通知（30s 超时）
     * GET /api/messages/poll
     */
    @GetMapping("/poll")
    public Result pollNotification() {
        return messageService.pollNotification();
    }

    /**
     * 未读总数
     * GET /api/messages/unread
     */
    @GetMapping("/unread")
    public Result getUnreadCount() {
        return messageService.getUnreadCount();
    }
}
