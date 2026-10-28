package com.exam.exam_system.service;

import com.exam.exam_system.entity.Course;

import java.util.List;

/**
 * 课程 Service
 */
public interface CourseService {

    /** 查全部课程（下拉框用） */
    List<Course> listAll();

    /**
     * 关键字模糊查询课程（下拉远端搜索用）
     * @param keyword 课程名称/编码关键字，为空时返回前 limit 条
     * @param limit   返回条数上限
     */
    List<Course> search(String keyword, int limit);

    /** 新增课程（课程名不可重复） */
    Course create(Course course);

    /** 更新课程信息（课程名不可与其它课程重复） */
    void update(Course course);

    /**
     * 教师维护本人任教课程的介绍信息（教师端「我的课程 → 课程信息」用）
     *
     * <p>只更新课程名称/类型/简介/详细介绍/学习目标/适用人群/大纲/封面/开课时间/报名截止；
     * <b>课程编码与任课教师绑定不在教师权限内</b>，提交时会被忽略，
     * 由 {@link #update(Course)}（管理员）统一维护。
     */
    void updateByTeacher(Course course);

    /** 删除课程：若被题目/试卷/考试/知识点引用则抛异常 */
    void delete(Long id);

    /**
     * 删除课程（可强制）
     * @param force 仅当课程下只剩知识点引用（无题目/试卷/考试引用）时为 true，
     *              会先连带清理该课程下知识点再删除课程
     */
    void delete(Long id, boolean force);

    /**
     * 课程详情：基础信息 + 任课教师列表 + 试题/试卷/考试/知识点数量统计
     */
    java.util.Map<String, Object> getDetail(Long id);

    /**
     * 上传课程封面图片（教师 / 管理员）
     *
     * <p>落盘到 upload.dir/course-cover/yyyyMM/ 下，返回可访问路径（/uploads/course-cover/...），
     * 由课程新增/编辑表单保存到 sys_course.cover。
     */
    String uploadCover(org.springframework.web.multipart.MultipartFile file);

    /**
     * Excel 批量导入课程
     * 列：课程名称 | 课程编码 | 课程描述（第一行表头跳过）
     * @return {success, failed, failedReasons}
     */
    java.util.Map<String, Object> importExcel(org.springframework.web.multipart.MultipartFile file);

    /**
     * 批量删除课程（部分失败策略：无引用的真删，被引用的逐条跳过并报告）
     * 删除前先检查 4 种业务引用（试题/试卷/考试/知识点），按引用强度排序只报最相关原因
     * @return {success, failed, failedReasons}
     */
    java.util.Map<String, Object> deleteBatch(java.util.List<Long> ids);

    /**
     * 一键随机分配任课教师（仅管理员）
     *
     * <p>从全部启用教师（role=1 且 status=1）中随机为每门课程挑选
     * minCount~maxCount 名教师绑定；skipBound=true 时跳过已有任课教师的课程。
     *
     * @param courseIds 目标课程ID；为空或 null 表示全部课程
     * @param minCount  每门课程教师数下限
     * @param maxCount  每门课程教师数上限
     * @param skipBound 是否跳过已有任课教师的课程
     * @return {total, success, skipped, failed, teacherCount, failedReasons}
     */
    java.util.Map<String, Object> assignTeachersRandomly(java.util.List<Long> courseIds,
                                                         int minCount, int maxCount,
                                                         boolean skipBound);
}
