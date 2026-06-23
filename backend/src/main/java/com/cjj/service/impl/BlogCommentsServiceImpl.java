package com.cjj.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjj.dto.Result;
import com.cjj.dto.UserDTO;
import com.cjj.entity.BlogComments;
import com.cjj.entity.User;
import com.cjj.mapper.BlogCommentsMapper;
import com.cjj.service.IBlogCommentsService;
import com.cjj.service.IUserService;
import com.cjj.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * city-review 探店笔记评论服务实现
 */
@Slf4j
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments>
        implements IBlogCommentsService {

    @Resource
    private IUserService userService;

    /**
     * 发表评论
     */
    public Result saveComment(BlogComments comment) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail(401, "请先登录");
        }
        comment.setUserId(user.getId());
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        comment.setLiked(0);
        comment.setStatus(false);

        boolean saved = save(comment);
        if (!saved) {
            return Result.fail("评论失败");
        }

        // 更新笔记评论数
        // blogService.update().setSql("comments = comments + 1").eq("id", comment.getBlogId()).update();

        log.info("city-review 评论成功 → blogId={}, userId={}", comment.getBlogId(), user.getId());
        return Result.ok(comment.getId());
    }

    /**
     * 查询评论列表（一级评论 + 子评论）
     */
    public Result queryComments(Long blogId) {
        // 查询一级评论
        List<BlogComments> comments = getBaseMapper().selectList(
                Wrappers.<BlogComments>lambdaQuery()
                        .eq(BlogComments::getBlogId, blogId)
                        .eq(BlogComments::getParentId, 0L)
                        .orderByAsc(BlogComments::getCreateTime));

        // 查询子评论
        List<BlogComments> subComments = getBaseMapper().selectList(
                Wrappers.<BlogComments>lambdaQuery()
                        .eq(BlogComments::getBlogId, blogId)
                        .ne(BlogComments::getParentId, 0L)
                        .orderByAsc(BlogComments::getCreateTime));

        return Result.ok(new Object() {
            public final List<BlogComments> root = comments;
            public final List<BlogComments> children = subComments;
        });
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
        return Result.ok();
    }
}
