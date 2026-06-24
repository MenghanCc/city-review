package com.cjj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_message")
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long fromUserId;

    private Long toUserId;

    private String content;

    private Boolean isRead;

    private LocalDateTime createdAt;

    // ---------- 非数据库字段 ----------

    @TableField(exist = false)
    private String fromUserNickname;

    @TableField(exist = false)
    private String fromUserAvatar;

    @TableField(exist = false)
    private String toUserNickname;

    @TableField(exist = false)
    private String toUserAvatar;

    /** 会话未读数 */
    @TableField(exist = false)
    private Integer unreadCount;
}
