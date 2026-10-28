package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习资料实体，对应 study_resource 表
 *
 * <p>资料归属课程（course_id），管理员/教师上传 PDF/Word/PPT/Excel 等文件，
 * 学生在「自主练习」页查看并下载对应课程资料。
 */
@Data
public class StudyResource {

    private Long id;

    /** 所属课程ID（sys_course.id） */
    private Long courseId;

    /** 课程名称（联表查出，非表字段，列表展示用） */
    private String courseName;

    /** 资料名称 */
    private String resourceName;

    /** 文件类型：pdf/word/ppt/excel/text/compress/other */
    private String fileType;

    /** 文件存储相对路径（/uploads/study-resource/...） */
    private String filePath;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 文件大小显示文本（如 1.2 MB） */
    private String fileSizeText;

    /** 上传人ID（sys_user.id） */
    private Long uploadTeacherId;

    /** 上传人姓名（冗余，列表直接展示） */
    private String uploadTeacherName;

    /** 备注 */
    private String description;

    /** 浏览次数（教师/管理员、学生在线预览时 +1） */
    private Integer viewCount;

    /** 下载次数（教师/管理员、学生下载资料时 +1） */
    private Integer downloadCount;

    /** 上传时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 逻辑删除：0未删 1已删 */
    private Integer isDeleted;
}
