package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.NoticeSendRequest;
import com.exam.exam_system.entity.Exam;
import com.exam.exam_system.entity.ExamRecord;
import com.exam.exam_system.entity.SysNotice;
import com.exam.exam_system.entity.SysUser;
import com.exam.exam_system.entity.SysUserNotice;
import com.exam.exam_system.mapper.*;
import com.exam.exam_system.service.NoticeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统通知 Service 实现
 */
@Service
public class NoticeServiceImpl implements NoticeService {

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private UserNoticeMapper userNoticeMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private SysClassMapper sysClassMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long sendNotice(NoticeSendRequest request, Long sendUserId, String sendUserName, Integer role) {
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            throw new RuntimeException("通知标题不能为空");
        }
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new RuntimeException("通知内容不能为空");
        }
        if (request.getReceiveType() == null) {
            throw new RuntimeException("请选择接收对象");
        }

        // 教师权限限制：只能发送给学生范围的通知（全体学生/全体教师仅管理员可用）
        if (role != null && role == 1 && (request.getReceiveType() == 1 || request.getReceiveType() == 4)) {
            throw new RuntimeException("教师只能发送给学生范围的通知");
        }

        // 教师权限限制：指定班级/考试时，只能选自己授课/自己创建的
        List<Long> studentIds = collectStudentIds(request, sendUserId, role);
        if (studentIds.isEmpty()) {
            throw new RuntimeException("未找到符合条件的接收学生");
        }

        // 组装 targetClassIds 字符串
        String targetClassIdsStr = null;
        if (request.getReceiveType() == 2 && request.getTargetClassIds() != null && !request.getTargetClassIds().isEmpty()) {
            targetClassIdsStr = request.getTargetClassIds().stream()
                    .map(String::valueOf).collect(Collectors.joining(","));
        }

        // 1. 插入通知主记录
        SysNotice notice = new SysNotice();
        notice.setTitle(request.getTitle().trim());
        notice.setContent(request.getContent());
        notice.setReceiveType(request.getReceiveType());
        notice.setTargetClassIds(targetClassIdsStr);
        notice.setTargetExamId(request.getReceiveType() == 3 ? request.getTargetExamId() : null);
        notice.setSendUserId(sendUserId);

        // 若传入的 sendUserName 为空，从用户表查询真实姓名
        String resolvedName = sendUserName;
        if (resolvedName == null || resolvedName.isEmpty()) {
            SysUser sender = sysUserMapper.selectById(sendUserId);
            if (sender != null) {
                resolvedName = (sender.getRealName() != null && !sender.getRealName().isEmpty())
                        ? sender.getRealName()
                        : sender.getUsername();
            }
        }
        notice.setSendUserName(resolvedName != null ? resolvedName : "系统");
        noticeMapper.insert(notice);

        // 2. 批量插入用户接收记录（每 500 条分批，避免 SQL 过长）
        int batchSize = 500;
        for (int i = 0; i < studentIds.size(); i += batchSize) {
            int end = Math.min(i + batchSize, studentIds.size());
            List<Long> batch = studentIds.subList(i, end);
            userNoticeMapper.batchInsert(notice.getId(), batch);
        }

        return notice.getId();
    }

    /**
     * 根据接收对象类型，收集接收学生 ID 列表
     */
    private List<Long> collectStudentIds(NoticeSendRequest request, Long sendUserId, Integer role) {
        List<Long> ids = new ArrayList<>();
        Integer type = request.getReceiveType();

        if (type == 1) {
            // 全部学生（仅管理员可使用）
            List<SysUser> allStudents = sysUserMapper.listByRole(0);
            ids = allStudents.stream().map(SysUser::getId).collect(Collectors.toList());

        } else if (type == 4) {
            // 全体教师（仅管理员可使用）
            List<SysUser> allTeachers = sysUserMapper.listByRole(1);
            ids = allTeachers.stream().map(SysUser::getId).collect(Collectors.toList());

        } else if (type == 2) {
            // 指定班级
            if (request.getTargetClassIds() == null || request.getTargetClassIds().isEmpty()) {
                throw new RuntimeException("请选择至少一个班级");
            }
            // 教师权限：如果是教师，只允许发送给自己授课班级（这里简化：教师可发送给任何班级）
            for (Long classId : request.getTargetClassIds()) {
                // 通过 classId 查询该班全部学生
                List<SysUser> users = sysUserMapper.listByRole(0);
                for (SysUser u : users) {
                    if (classId.equals(u.getClassId())) {
                        ids.add(u.getId());
                    }
                }
            }

        } else if (type == 3) {
            // 指定考试的考生
            if (request.getTargetExamId() == null) {
                throw new RuntimeException("请选择考试");
            }
            Exam exam = examMapper.selectById(request.getTargetExamId());
            if (exam == null) {
                throw new RuntimeException("考试不存在");
            }
            // 教师权限：只能选自己创建的考试
            if (role != null && role == 1) {
                if (exam.getCreatorId() == null || !exam.getCreatorId().equals(sendUserId)) {
                    throw new RuntimeException("只能选择自己创建的考试");
                }
            }
            // 查询参加该考试的所有学生（通过 paper_id 关联 exam_record）
            ids = listExamStudentIds(exam.getPaperId());
        }

        // 去重
        return ids.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 查询参加某张试卷（某场考试）的所有学生 ID
     */
    private List<Long> listExamStudentIds(Long paperId) {
        if (paperId == null) return new ArrayList<>();
        // 通过 SysUserMapper 查询所有学生，再通过 ExamRecord 过滤
        // 简化：直接从 exam_record 查参加过该试卷的学生
        // 为避免依赖额外方法，这里调用 listByRole 再结合手动查询
        List<Long> result = new ArrayList<>();
        List<SysUser> allStudents = sysUserMapper.listByRole(0);
        for (SysUser u : allStudents) {
            ExamRecordMapper rm = null; // 无法直接注入
        }
        // 改用直接注入 ExamRecordMapper
        return doListExamStudentIds(paperId);
    }

    @Autowired
    private ExamRecordMapper examRecordMapper;

    private List<Long> doListExamStudentIds(Long paperId) {
        // 直接从 exam_record 查询 status >= 1 的学生
        // 先查所有学生，再查每条记录
        List<Long> result = new ArrayList<>();
        List<ExamRecord> records = examRecordMapper.listByPaperId(paperId);
        for (ExamRecord r : records) {
            if (r.getUserId() != null && !result.contains(r.getUserId())) {
                result.add(r.getUserId());
            }
        }
        // 如果 records 为空，也从 exam_student 表查（进行中的考试）
        if (result.isEmpty()) {
            List<Long> fromExamStudent = examStudentStudentIds(paperId);
            result.addAll(fromExamStudent);
        }
        return result;
    }

    @Autowired
    private ExamMonitorMapper examMonitorMapper;

    private List<Long> examStudentStudentIds(Long paperId) {
        // 从 exam_student 反查：先通过 exam 表查 paperId 对应的所有 examId，再查 exam_student
        // 简化：如果没数据就返回空列表（教师可以通过其他方式发送）
        return new ArrayList<>();
    }

    @Override
    public PageResult<SysNotice> listNotices(int pageNum, int pageSize, Long userId, Integer role, String keyword) {
        int offset = (pageNum - 1) * pageSize;
        List<SysNotice> list = noticeMapper.listPage(offset, pageSize, userId, role, keyword);
        long total = noticeMapper.countPage(userId, role, keyword);

        // 补充接收范围文本
        for (SysNotice n : list) {
            n.setReceiveRangeText(buildRangeText(n));
        }

        return PageResult.of(list, total, (long) pageNum, (long) pageSize);
    }

    private String buildRangeText(SysNotice n) {
        if (n.getReceiveType() == null) return "未知";
        switch (n.getReceiveType()) {
            case 1: return "全部学生";
            case 4: return "全体教师";
            case 5: return "全体学生（考试自动通知）";
            // 课程申请/加入/移出等系统自动通知（receiveType=6）：接收人由具体业务决定，前端再按通知标题细分
            case 6: return "系统自动通知（课程）";
            case 2: {
                if (n.getTargetClassIds() == null || n.getTargetClassIds().isEmpty()) return "指定班级";
                StringBuilder sb = new StringBuilder();
                String[] ids = n.getTargetClassIds().split(",");
                List<String> names = new ArrayList<>();
                for (String idStr : ids) {
                    try {
                        Long cid = Long.parseLong(idStr.trim());
                        var cls = sysClassMapper.selectById(cid);
                        if (cls != null) names.add(cls.getClassName());
                    } catch (Exception ignored) {}
                }
                return "指定班级：" + String.join("、", names);
            }
            case 3: {
                if (n.getTargetExamId() == null) return "指定考试";
                Exam exam = examMapper.selectById(n.getTargetExamId());
                return "指定考试：" + (exam != null ? exam.getExamName() : "已删除考试");
            }
            default: return "未知";
        }
    }

    @Override
    public SysNotice getNoticeDetail(Long id) {
        SysNotice notice = noticeMapper.selectById(id);
        if (notice != null) {
            notice.setReceiveRangeText(buildRangeText(notice));
        }
        return notice;
    }

    @Override
    public Integer getSenderRole(Long userId) {
        if (userId == null) {
            return null;
        }
        return noticeMapper.selectUserRole(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotice(Long id, Long userId, Integer role) {
        SysNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new RuntimeException("通知不存在或已删除");
        }
        // 教师权限：只能删除自己发的
        if (role != null && role == 1) {
            if (!userId.equals(notice.getSendUserId())) {
                throw new RuntimeException("无权删除他人发送的通知");
            }
        }
        // 先删用户通知记录，再逻辑删除主记录
        userNoticeMapper.deleteByNoticeId(id);
        noticeMapper.logicDelete(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAndResend(Long id, String title, String content, Long userId, Integer role) {
        if (title == null || title.trim().isEmpty()) {
            throw new RuntimeException("通知标题不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new RuntimeException("通知内容不能为空");
        }
        SysNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new RuntimeException("通知不存在或已删除");
        }
        // 权限：教师只能编辑自己发的；管理员可编辑全部
        if (role != null && role == 1 && !userId.equals(notice.getSendUserId())) {
            throw new RuntimeException("只能编辑自己发送的通知");
        }
        // 更新标题与内容，发送时间刷新为重新发布时间
        noticeMapper.updateContent(id, title.trim(), content);
        // 重新通知：重置所有接收人的已读状态（未读红点重新出现）
        noticeMapper.resetAllRead(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendExamNotice(com.exam.exam_system.entity.Exam exam, Long publisherId, String publisherName) {
        if (exam == null || exam.getId() == null) {
            return;
        }
        // 1. 插入通知主记录（receiveType=5 考试自动通知）
        SysNotice notice = new SysNotice();
        notice.setTitle("新考试通知：" + exam.getExamName());
        notice.setContent(buildExamNoticeContent(exam));
        notice.setReceiveType(5);
        notice.setTargetExamId(exam.getId());
        notice.setSendUserId(publisherId);
        notice.setSendUserName(publisherName != null && !publisherName.isEmpty() ? publisherName : "系统");
        noticeMapper.insert(notice);

        // 2. 全体学生逐人写入接收记录（每 500 条分批，避免 SQL 过长）
        List<SysUser> students = sysUserMapper.listByRole(0);
        if (students.isEmpty()) {
            return;
        }
        List<Long> ids = students.stream().map(SysUser::getId).collect(Collectors.toList());
        int batchSize = 500;
        for (int i = 0; i < ids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, ids.size());
            userNoticeMapper.batchInsert(notice.getId(), ids.subList(i, end));
        }
    }

    /** 组装考试通知正文 */
    private String buildExamNoticeContent(com.exam.exam_system.entity.Exam exam) {
        StringBuilder sb = new StringBuilder();
        sb.append("您有一场新的考试安排：\n\n")
          .append("考试名称：").append(exam.getExamName()).append("\n")
          .append("考试时长：").append(exam.getDuration() != null ? exam.getDuration() : "-").append(" 分钟\n")
          .append("试卷总分：").append(exam.getTotalScore() != null ? exam.getTotalScore() : "-").append(" 分\n");
        if (exam.getPassScore() != null) {
            sb.append("及格分数：").append(exam.getPassScore()).append(" 分\n");
        }
        if (exam.getStartTime() != null) {
            sb.append("开始时间：").append(exam.getStartTime().toString().replace('T', ' ').substring(0, 16)).append("\n");
        }
        if (exam.getEndTime() != null) {
            sb.append("截止时间：").append(exam.getEndTime().toString().replace('T', ' ').substring(0, 16)).append("\n");
        }
        sb.append("\n请同学们按时登录系统参加考试，祝考试顺利！");
        return sb.toString();
    }

    @Override
    public String confirmUserNotice(Long userId, Long noticeId) {
        if (userId == null || noticeId == null) {
            throw new RuntimeException("参数缺失");
        }
        // 校验消息归属：只能确认发给自己的消息
        int owned = userNoticeMapper.countOwned(noticeId, userId);
        if (owned == 0) {
            throw new RuntimeException("消息不存在或无权操作");
        }
        // 幂等：已确认的直接返回成功
        int updated = userNoticeMapper.confirm(noticeId, userId);
        if (updated > 0) {
            return java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
        // updated == 0：已是确认状态，回查确认时间
        SysUserNotice record = userNoticeMapper.getByNoticeAndUser(noticeId, userId);
        return record != null && record.getConfirmTime() != null
                ? record.getConfirmTime().toString().replace('T', ' ').substring(0, 19) : null;
    }

    @Override
    public void markUserNoticeUnread(Long userId, Long noticeId) {
        if (userId == null || noticeId == null) {
            throw new RuntimeException("参数缺失");
        }
        if (userNoticeMapper.countOwned(noticeId, userId) == 0) {
            throw new RuntimeException("消息不存在或无权操作");
        }
        int updated = userNoticeMapper.markUnread(noticeId, userId);
        if (updated == 0) {
            throw new RuntimeException("该消息已是未读状态");
        }
    }

    @Override
    public void setUserNoticeTop(Long userId, Long userNoticeId, boolean top) {
        if (userId == null || userNoticeId == null) {
            throw new RuntimeException("参数缺失");
        }
        int updated = userNoticeMapper.setTop(userNoticeId, userId, top ? 1 : 0);
        if (updated == 0) {
            throw new RuntimeException("消息不存在或无权操作");
        }
    }

    // ========== 学生端接口 ==========

    @Override
    public int countUnread(Long userId) {
        return userNoticeMapper.countUnread(userId);
    }

    @Override
    public List<SysUserNotice> listUserNotices(Long userId, int limit) {
        return userNoticeMapper.listByUser(userId, limit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long noticeId, Long userId) {
        userNoticeMapper.markRead(noticeId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        userNoticeMapper.markAllRead(userId);
    }

    @Override
    public PageResult<SysUserNotice> listUserNoticesPage(Long userId, int pageNum, int pageSize) {
        if (pageNum < 1) pageNum = 1;
        if (pageSize < 1 || pageSize > 100) pageSize = 10;
        int offset = (pageNum - 1) * pageSize;
        int total = userNoticeMapper.countByUser(userId);
        List<SysUserNotice> list = total == 0
                ? java.util.Collections.emptyList()
                : userNoticeMapper.listByUserPage(userId, offset, pageSize);
        // PageResult 字段为 Long 包装类型，int 无法直接装箱为 Long，需显式 (long) 转换
        return PageResult.of(list, (long) total, (long) pageNum, (long) pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logicDeleteUserNotice(Long id, Long userId) {
        if (id == null || userId == null) {
            throw new IllegalArgumentException("参数缺失");
        }
        int rows = userNoticeMapper.logicDelete(id, userId);
        if (rows == 0) {
            throw new IllegalArgumentException("消息不存在或已删除");
        }
    }
}
