package com.cjj.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("tb_wallet_transaction")
public class WalletTransaction implements Serializable {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer transactionType; // 1=充值 2=消费 3=退款
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Long orderId;
    private String note;
    private LocalDateTime createdAt;
}
