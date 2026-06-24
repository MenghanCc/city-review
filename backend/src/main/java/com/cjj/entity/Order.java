package com.cjj.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_order")
public class Order implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer orderType; // 1=优惠券 2=商品
    private Long targetId;
    private Long shopId;
    private BigDecimal amount;
    private Integer status; // 0=待支付 1=已支付 2=已取消
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String targetName;
    @TableField(exist = false)
    private String shopName;
}
