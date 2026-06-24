package com.cjj.controller;

import com.cjj.dto.Result;
import com.cjj.service.impl.BlogCommentsServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Map;

/**
 * city-review 评论控制器
 * POST /api/comments       → 发表评论
 * GET  /api/comments/blog/{blogId} → 查询评论列表（含用户信息）
 */
@RestController
@RequestMapping("/api/comments")
public class CommentController {

    @Resource
    private BlogCommentsServiceImpl commentsService;

    /**
     * 发表评论
     * 请求体：{ "blogId": 1, "content": "这家店看起来不错！" }
     */
    @PostMapping
    public Result createComment(@RequestBody Map<String, Object> body) {
        Object blogIdObj = body.get("blogId");
        Object contentObj = body.get("content");

        if (blogIdObj == null || contentObj == null) {
            return Result.fail("blogId 和 content 不能为空");
        }

        Long blogId = Long.valueOf(blogIdObj.toString());
        String content = contentObj.toString().trim();

        if (content.isEmpty()) {
            return Result.fail("评论内容不能为空");
        }
        if (content.length() > 500) {
            return Result.fail("评论内容不能超过500字");
        }

        return commentsService.saveComment(blogId, content);
    }

    /**
     * 获取评论列表（含用户昵称/头像，最新在前）
     */
    @GetMapping("/blog/{blogId}")
    public Result getComments(@PathVariable Long blogId) {
        return commentsService.queryComments(blogId);
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable Long id) {
        return commentsService.deleteComment(id);
    }
}
