package com.cjj.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cjj.dto.Result;
import com.cjj.dto.UserDTO;
import com.cjj.entity.Message;
import com.cjj.entity.User;
import com.cjj.mapper.MessageMapper;
import com.cjj.service.IUserService;
import com.cjj.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> {

    @Resource
    private IUserService userService;

    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    // 每个用户一个阻塞队列，用于长轮询唤醒
    private final ConcurrentHashMap<Long, BlockingQueue<String>> notifyQueues = new ConcurrentHashMap<>();

    /**
     * 发送消息
     */
    public Result sendMessage(Long toUserId, String content) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");
        if (toUserId == null || content == null || content.trim().isEmpty()) {
            return Result.fail("参数不完整");
        }
        if (me.getId().equals(toUserId)) {
            return Result.fail("不能给自己发消息");
        }

        User receiver = userService.getById(toUserId);
        if (receiver == null) return Result.fail("接收者不存在");

        Message msg = new Message();
        msg.setFromUserId(me.getId());
        msg.setToUserId(toUserId);
        msg.setContent(content.trim());
        msg.setIsRead(false);
        msg.setCreatedAt(LocalDateTime.now());
        save(msg);

        // 通过 Redis Pub/Sub 通知接收者
        stringRedisTemplate.convertAndSend("msg:notify:" + toUserId, me.getId().toString());
        // 本地唤醒接收者的长轮询队列
        BlockingQueue<String> q = notifyQueues.get(toUserId);
        if (q != null) q.offer("new");

        log.info("city-review 消息发送 → from={}, to={}", me.getId(), toUserId);
        return Result.ok(msg.getId());
    }

    /**
     * 会话列表：当前用户与每个联系人的最新一条消息 + 未读数
     */
    public Result getConversations() {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");
        Long myId = me.getId();

        // 查询所有与我相关的消息（我是发送者或接收者）
        List<Message> all = getBaseMapper().selectList(
                Wrappers.<Message>lambdaQuery()
                        .and(w -> w.eq(Message::getFromUserId, myId).or().eq(Message::getToUserId, myId))
                        .orderByDesc(Message::getCreatedAt));

        // 按"对方用户ID"分组，取每组最新一条作为 lastMessage
        Map<Long, Message> lastMsgMap = new LinkedHashMap<>();
        Map<Long, Integer> unreadMap = new HashMap<>();

        for (Message m : all) {
            Long peerId = m.getFromUserId().equals(myId) ? m.getToUserId() : m.getFromUserId();
            // 最新一条
            if (!lastMsgMap.containsKey(peerId)) {
                lastMsgMap.put(peerId, m);
            }
            // 未读计数（发给我的且未读）
            if (m.getToUserId().equals(myId) && !Boolean.TRUE.equals(m.getIsRead())) {
                unreadMap.put(peerId, unreadMap.getOrDefault(peerId, 0) + 1);
            }
        }

        if (lastMsgMap.isEmpty()) return Result.ok(Collections.emptyList());

        // 批量查用户信息
        Set<Long> peerIds = lastMsgMap.keySet();
        Map<Long, User> userMap = userService.listByIds(peerIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Long, Message> e : lastMsgMap.entrySet()) {
            Long peerId = e.getKey();
            Message m = e.getValue();
            User u = userMap.get(peerId);

            Map<String, Object> item = new HashMap<>();
            item.put("userId", peerId);
            item.put("userNickname", u != null ? u.getNickName() : "未知");
            item.put("userAvatar", u != null ? u.getIcon() : "");
            item.put("lastMessage", m.getContent());
            item.put("lastTime", m.getCreatedAt());
            item.put("unreadCount", unreadMap.getOrDefault(peerId, 0));
            list.add(item);
        }

        return Result.ok(list);
    }

    /**
     * 与某人的聊天记录（双向），按时间正序
     */
    public Result getMessagesWith(Long peerId) {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");
        Long myId = me.getId();

        List<Message> msgs = getBaseMapper().selectList(
                Wrappers.<Message>lambdaQuery()
                        .and(w -> w
                                .and(a -> a.eq(Message::getFromUserId, myId).eq(Message::getToUserId, peerId))
                                .or(a -> a.eq(Message::getFromUserId, peerId).eq(Message::getToUserId, myId)))
                        .orderByAsc(Message::getCreatedAt));

        // 标记对方发来的未读消息为已读
        for (Message m : msgs) {
            if (m.getToUserId().equals(myId) && !Boolean.TRUE.equals(m.getIsRead())) {
                m.setIsRead(true);
                updateById(m);
            }
        }

        // 填充昵称和头像
        User peer = userService.getById(peerId);
        User myself = userService.getById(myId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Message m : msgs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", m.getId());
            item.put("fromUserId", m.getFromUserId());
            item.put("toUserId", m.getToUserId());
            item.put("content", m.getContent());
            item.put("isRead", m.getIsRead());
            item.put("createdAt", m.getCreatedAt());
            item.put("fromNickname", m.getFromUserId().equals(myId)
                    ? (myself != null ? myself.getNickName() : "我")
                    : (peer != null ? peer.getNickName() : ""));
            item.put("fromAvatar", m.getFromUserId().equals(myId)
                    ? (myself != null ? myself.getIcon() : "")
                    : (peer != null ? peer.getIcon() : ""));
            list.add(item);
        }

        return Result.ok(list);
    }

    /**
     * 长轮询等待新消息通知（30s 超时）
     */
    public Result pollNotification() {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");

        BlockingQueue<String> q = notifyQueues.computeIfAbsent(me.getId(), k -> new LinkedBlockingQueue<>(1));
        try {
            String signal = q.poll(30, TimeUnit.SECONDS);
            return Result.ok(signal != null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.ok(false);
        }
    }

    /**
     * 总未读消息数
     */
    public Result getUnreadCount() {
        UserDTO me = UserHolder.getUser();
        if (me == null) return Result.fail(401, "请先登录");

        int count = getBaseMapper().selectCount(
                Wrappers.<Message>lambdaQuery()
                        .eq(Message::getToUserId, me.getId())
                        .eq(Message::getIsRead, false));
        return Result.ok(count);
    }
}
