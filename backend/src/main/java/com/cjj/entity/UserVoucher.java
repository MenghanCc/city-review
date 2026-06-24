package com.cjj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_user_voucher")
public class UserVoucher implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long voucherId;
    private Long shopId;
    private Integer status; // 0=未使用 1=已使用 2=已过期
    private LocalDateTime buyTime;
    private LocalDateTime expireTime;
    private LocalDateTime useTime;

    @TableField(exist = false)
    private String voucherTitle;
    @TableField(exist = false)
    private Long faceValue;
    @TableField(exist = false)
    private String shopName;
}
