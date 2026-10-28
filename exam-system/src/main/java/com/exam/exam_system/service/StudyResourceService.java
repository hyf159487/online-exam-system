package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.StudyResourceQuery;
import com.exam.exam_system.entity.StudyResource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 学习资料 Service 接口
 */
public interface StudyResourceService {

    /** 分页查学习资料（管理端列表 + 导出源数据） */
    PageResult<StudyResource> page(StudyResourceQuery query);

    /** 上传资料：文件落盘 + 保存记录 */
    StudyResource upload(Long courseId, String resourceName, String description,
                         MultipartFile file, Long uploadTeacherId, String uploadTeacherName);

    /** 更新资料（名称/备注/所属课程） */
    void update(Long id, String resourceName, String description, Long courseId);

    /**
     * 新增「外部链接」资料（第三方教学视频等站外资源地址，无需上传文件）
     * <p>file_type 固定为 link，链接地址存入 file_path，学生点击后跳转站外学习。
     */
    StudyResource addLink(Long courseId, String resourceName, String linkUrl, String description,
                          Long uploadTeacherId, String uploadTeacherName);

    /** 修改外部链接资料的链接地址（仅 file_type=link 的资料可改） */
    void updateLinkUrl(Long id, String linkUrl);

    /** 删除单个资料（删记录 + 删服务器文件） */
    void delete(Long id);

    /** 批量删除（删记录 + 删服务器文件，部分失败策略） */
    Map<String, Object> deleteBatch(List<Long> ids);

    /** 按 id 查详情 */
    StudyResource getById(Long id);

    /** 浏览次数 +1（管理员/教师/学生在线预览时调用） */
    void incrementViewCount(Long id);

    /** 下载次数 +1（管理员/教师/学生下载资料时调用） */
    void incrementDownloadCount(Long id);

    /** 学生端：按课程拉取该课程全部资料 */
    List<StudyResource> listByCourse(Long courseId);

    /**
     * 学生端：学生已加入课程的全部资料
     *
     * @param studentId 学生ID
     * @param courseId  可选，填了只过滤该课程；为空返回已加入课程的全部资料
     */
    List<StudyResource> listByStudentJoined(Long studentId, Long courseId);

    /** 导出清单数据（不受分页限制，按筛选条件返回全部） */
    List<StudyResource> listForExport(StudyResourceQuery query);
}
