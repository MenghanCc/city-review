package com.cjj.controller;

import com.cjj.dto.Result;
import com.cjj.entity.BlogComments;
import com.cjj.service.impl.BlogCommentsServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * city-review 探店笔记评论控制器
 */
@RestController
@RequestMapping("/api/blog-comments")
public class BlogCommentsController {

    @Resource
    private BlogCommentsServiceImpl commentsService;

    /**
     * 发表评论（兼容旧接口，委托到新方法）
     */
    @PostMapping
    public Result saveComment(@RequestBody BlogComments comment) {
        return commentsService.saveComment(comment.getBlogId(), comment.getContent());
    }

    /**
     * 查询评论列表
     */
    @GetMapping("/{blogId}")
    public Result queryComments(@PathVariable("blogId") Long blogId) {
        return commentsService.queryComments(blogId);
    }

    /**
     * 删除评论
     */
    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Long id) {
        return commentsService.deleteComment(id);
    }
}
