package com.cjj.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjj.dto.CommentVO;
import com.cjj.dto.Result;
import com.cjj.dto.UserDTO;
import com.cjj.entity.Blog;
import com.cjj.entity.BlogComments;
import com.cjj.entity.User;
import com.cjj.mapper.BlogCommentsMapper;
import com.cjj.mapper.BlogMapper;
import com.cjj.service.IBlogCommentsService;
import com.cjj.service.IUserService;
import com.cjj.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * city-review 探店笔记评论服务实现
 */
@Slf4j
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments>
        implements IBlogCommentsService {

    @Resource
    private IUserService userService;

    @Resource
    private BlogMapper blogMapper;

    /**
     * 发表评论，并基于 COUNT 实时更新帖子评论数
     * @param blogId 帖子ID
     * @param content 评论内容
     * @return 评论ID + 最新评论数
     */
    public Result saveComment(Long blogId, String content) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }

        BlogComments comment = new BlogComments();
        comment.setUserId(user.getId());
        comment.setBlogId(blogId);
        comment.setContent(content);
        comment.setParentId(0L);
        comment.setAnswerId(0L);
        comment.setLiked(0);
        comment.setStatus(false);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());

        boolean saved = save(comment);
        if (!saved) {
            return Result.fail("评论失败");
        }

        // 关键：基于 COUNT 子查询更新帖子评论数，严禁 comment_count + 1
        int count = getBaseMapper().selectCount(
                Wrappers.<BlogComments>lambdaQuery()
                        .eq(BlogComments::getBlogId, blogId));

        Blog blog = new Blog();
        blog.setId(blogId);
        blog.setComments(count);
        blogMapper.updateById(blog);

        log.info("city-review 评论成功 → blogId={}, userId={}, commentId={}, count={}",
                blogId, user.getId(), comment.getId(), count);

        Map<String, Object> result = new HashMap<>();
        result.put("commentId", comment.getId());
        result.put("commentCount", count);
        return Result.ok(result);
    }

    /**
     * 查询评论列表（含用户昵称/头像，最新在前）
     */
    public Result queryComments(Long blogId) {
        // 查询一级评论（parent_id = 0），按时间倒序
        List<BlogComments> comments = getBaseMapper().selectList(
                Wrappers.<BlogComments>lambdaQuery()
                        .eq(BlogComments::getBlogId, blogId)
                        .eq(BlogComments::getParentId, 0L)
                        .orderByDesc(BlogComments::getCreateTime));

        if (comments.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 批量查询用户信息
        Set<Long> userIds = comments.stream()
                .map(BlogComments::getUserId)
                .collect(Collectors.toSet());
        List<User> users = userService.listByIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        // 组装 CommentVO
        List<CommentVO> voList = comments.stream().map(c -> {
            CommentVO vo = new CommentVO();
            vo.setId(c.getId());
            vo.setUserId(c.getUserId());
            vo.setBlogId(c.getBlogId());
            vo.setContent(c.getContent());
            vo.setCreatedAt(c.getCreateTime());
            User u = userMap.get(c.getUserId());
            if (u != null) {
                vo.setUserNickname(u.getNickName());
                vo.setUserAvatar(u.getIcon());
            }
            return vo;
        }).collect(Collectors.toList());

        return Result.ok(voList);
    }

    /**
     * 删除评论
     */
    public Result deleteComment(Long commentId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        BlogComments comment = getById(commentId);
        if (comment == null) {
            return Result.fail("评论不存在");
        }
        if (!comment.getUserId().equals(user.getId())) {
            return Result.fail("无权删除他人评论");
        }
        removeById(commentId);

        // 更新帖子评论数
        int count = getBaseMapper().selectCount(
                Wrappers.<BlogComments>lambdaQuery()
                        .eq(BlogComments::getBlogId, comment.getBlogId()));
        Blog blog = new Blog();
        blog.setId(comment.getBlogId());
        blog.setComments(count);
        blogMapper.updateById(blog);

        return Result.ok();
    }
}
