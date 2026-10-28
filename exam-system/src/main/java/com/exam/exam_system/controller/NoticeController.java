package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.NoticeSendRequest;
import com.exam.exam_system.entity.SysNotice;
import com.exam.exam_system.entity.SysUserNotice;
import com.exam.exam_system.service.NoticeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息通知 Controller
 *
 * <p>接口清单：
 * <ul>
 *   <li>POST  /api/notices/send            发布通知（教师/管理员）</li>
 *   <li>GET   /api/notices/list            通知发送记录列表</li>
 *   <li>GET   /api/notices/detail/{id}     通知详情</li>
 *   <li>DELETE /api/notices/delete/{id}    删除通知</li>
 *   <li>GET   /api/notices/userList        学生获取自己的消息列表</li>
 *   <li>GET   /api/notices/unreadCount     学生未读消息数</li>
 *   <li>POST  /api/notices/read/{noticeId}  标记已读</li>
 *   <li>POST  /api/notices/readAll         全部标记已读</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

    @Autowired
    private NoticeService noticeService;

    /**
     * 发布通知
     */
    @PostMapping("/send")
    public Result<Long> send(@RequestBody NoticeSendRequest request,
                             HttpServletRequest httpReq) {
        Integer role = (Integer) httpReq.getAttribute("role");
        Long userId = (Long) httpReq.getAttribute("userId");

        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可发送通知");
        }
        try {
            // sendUserName 传 null，由 Service 内部根据 userId 查询真实姓名
            Long id = noticeService.sendNotice(request, userId, null, role);
            return Result.success("发送成功", id);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 通知发送记录列表
     */
    @GetMapping("/list")
    public Result<PageResult<SysNotice>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            HttpServletRequest httpReq) {
        Integer role = (Integer) httpReq.getAttribute("role");
        Long userId = (Long) httpReq.getAttribute("userId");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        PageResult<SysNotice> result = noticeService.listNotices(pageNum, pageSize, userId, role, keyword);
        return Result.success("查询成功", result);
    }

    /**
     * 通知详情
     */
    @GetMapping("/detail/{id}")
    public Result<SysNotice> detail(@PathVariable Long id,
                                    HttpServletRequest httpReq) {
        Integer role = (Integer) httpReq.getAttribute("role");
        Long userId = (Long) httpReq.getAttribute("userId");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        SysNotice notice = noticeService.getNoticeDetail(id);
        if (notice == null) {
            return Result.error(404, "通知不存在或已删除");
        }
        // 教师权限校验：可看自己发的 + 管理员发的，不能看其他教师发的
        if (role == 1 && !notice.getSendUserId().equals(userId)) {
            Integer senderRole = noticeService.getSenderRole(notice.getSendUserId());
            if (senderRole == null || senderRole != 2) {
                return Result.error(403, "无权查看他人发送的通知");
            }
        }
        return Result.success("查询成功", notice);
    }

    /**
     * 学生确认收到消息（幂等）
     */
    @PutMapping("/confirm/{noticeId}")
    public Result<?> confirm(@PathVariable Long noticeId,
                             HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        try {
            String confirmTime = noticeService.confirmUserNotice(userId, noticeId);
            return Result.success("确认成功", confirmTime);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 学生把已读消息重新标记为未读
     */
    @PutMapping("/userUnread/{noticeId}")
    public Result<?> markUnread(@PathVariable Long noticeId,
                                HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        try {
            noticeService.markUserNoticeUnread(userId, noticeId);
            return Result.success("已标记为未读");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 学生置顶 / 取消置顶自己的消息
     *
     * @param id  sys_user_notice 主键
     * @param top true 置顶 / false 取消置顶
     */
    @PutMapping("/userTop/{id}")
    public Result<?> setTop(@PathVariable Long id,
                            @RequestParam(defaultValue = "true") Boolean top,
                            HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        try {
            noticeService.setUserNoticeTop(userId, id, top);
            return Result.success(top ? "置顶成功" : "已取消置顶");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 编辑通知并重新发布
     * 更新标题/内容，发送时间刷新为当前时间，重置全部接收人已读状态（重新通知）
     * 权限：管理员可编辑全部；教师只能编辑自己发送的
     */
    @PutMapping("/update")
    public Result<?> update(@RequestBody SysNotice req,
                            HttpServletRequest httpReq) {
        Integer role = (Integer) httpReq.getAttribute("role");
        Long userId = (Long) httpReq.getAttribute("userId");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可编辑通知");
        }
        if (req.getId() == null) {
            return Result.error(400, "缺少通知ID");
        }
        try {
            // 只取 id/title/content 三个字段，其余字段（接收范围、发送人等）一律丢弃，防止篡改
            noticeService.updateAndResend(req.getId(), req.getTitle(), req.getContent(), userId, role);
            return Result.success("编辑成功，已重新通知全部接收人");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /**
     * 删除通知
     */
    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id,
                            HttpServletRequest httpReq) {
        Integer role = (Integer) httpReq.getAttribute("role");
        Long userId = (Long) httpReq.getAttribute("userId");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        try {
            noticeService.deleteNotice(id, userId, role);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    // ========== 学生端接口 ==========

    /**
     * 学生获取未读消息数
     */
    @GetMapping("/unreadCount")
    public Result<Integer> unreadCount(HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        int count = noticeService.countUnread(userId);
        return Result.success("查询成功", count);
    }

    /**
     * 学生获取自己的消息列表
     */
    @GetMapping("/userList")
    public Result<List<SysUserNotice>> userList(
            @RequestParam(defaultValue = "50") Integer limit,
            HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        List<SysUserNotice> list = noticeService.listUserNotices(userId, limit);
        return Result.success("查询成功", list);
    }

    /**
     * 标记某条通知已读
     */
    @PostMapping("/read/{noticeId}")
    public Result<?> read(@PathVariable Long noticeId,
                          HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        noticeService.markRead(noticeId, userId);
        return Result.success("操作成功");
    }

    /**
     * 全部标记已读
     */
    @PostMapping("/readAll")
    public Result<?> readAll(HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        noticeService.markAllRead(userId);
        return Result.success("已全部标记为已读");
    }

    /**
     * 学生分页查询自己的消息（消息通知页面用）
     * GET /api/notices/userPage?pageNum=1&pageSize=10
     */
    @GetMapping("/userPage")
    public Result<PageResult<SysUserNotice>> userPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        PageResult<SysUserNotice> page = noticeService.listUserNoticesPage(userId, pageNum, pageSize);
        return Result.success("查询成功", page);
    }

    /**
     * 学生逻辑删除自己的一条消息（按 user_notice 主键 id 删）
     * DELETE /api/notices/userDelete/{id}
     * 后端校验 user_id 防越权：只能删自己的
     */
    @DeleteMapping("/userDelete/{id}")
    public Result<?> userDelete(@PathVariable Long id, HttpServletRequest httpReq) {
        Long userId = (Long) httpReq.getAttribute("userId");
        if (userId == null) {
            return Result.error(401, "未登录");
        }
        try {
            noticeService.logicDeleteUserNotice(id, userId);
            return Result.success("已删除");
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }
}
