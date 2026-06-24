package com.cjj.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * city-review 评论视图对象（含用户昵称/头像）
 */
@Data
@Accessors(chain = true)
public class CommentVO {
    private Long id;
    private Long userId;
    private Long blogId;
    private String userNickname;
    private String userAvatar;
    private String content;
    private LocalDateTime createdAt;
}
