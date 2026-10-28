package com.exam.exam_system.controller;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.common.Result;
import com.exam.exam_system.dto.TeacherWrongQuery;
import com.exam.exam_system.mapper.SysClassMapper;
import com.exam.exam_system.service.WrongQuestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 教师端错题管理 Controller
 *
 * <p>放在 /api/teacher 下，仅教师(role=1) / 管理员(role=2) 可访问，
 * 用于查看全班错题、按学生/班级/学科/题型筛选，以及薄弱点统计。
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherWrongController {

    @Autowired
    private WrongQuestionService wrongQuestionService;

    @Autowired
    private SysClassMapper sysClassMapper;

    /** 教师数据权限：错题按「任教课程」隔离 */
    @Autowired
    private com.exam.exam_system.common.TeacherScope teacherScope;

    /** 全班错题列表（可按 学生 / 班级 / 学科 / 题型 筛选） */
    @GetMapping("/wrong-questions")
    public Result<PageResult<Map<String, Object>>> list(@ModelAttribute TeacherWrongQuery q,
                                                         HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        // 数据权限：教师只能看到自己任教课程下的错题（管理员为 null，不限制）
        q.setCourseIds(teacherScope.myCourseIds(request));
        return Result.success("查询成功", wrongQuestionService.teacherPage(q));
    }

    /** 错题统计：按学科分布 + 高频错题 Top10（教师讲评 / 薄弱点分析用） */
    @GetMapping("/wrong-questions/stats")
    public Result<Map<String, Object>> stats(@ModelAttribute TeacherWrongQuery q,
                                             HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        q.setCourseIds(teacherScope.myCourseIds(request));
        return Result.success("查询成功", wrongQuestionService.teacherStats(q));
    }

    /** 班级列表（教师端错题筛选下拉用） */
    @GetMapping("/classes")
    public Result<List<Map<String, Object>>> classes(HttpServletRequest request) {
        Integer role = (Integer) request.getAttribute("role");
        if (role == null || (role != 1 && role != 2)) {
            return Result.error(403, "仅教师或管理员可访问");
        }
        return Result.success("查询成功", sysClassMapper.listAll());
    }
}
