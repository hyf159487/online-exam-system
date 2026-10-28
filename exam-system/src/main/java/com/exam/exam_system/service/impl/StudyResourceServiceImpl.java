package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.StudyResourceQuery;
import com.exam.exam_system.entity.StudyResource;
import com.exam.exam_system.mapper.CourseMemberMapper;
import com.exam.exam_system.mapper.StudyResourceMapper;
import com.exam.exam_system.service.StudyResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 学习资料 Service 实现
 *
 * <p>文件存储：保存到配置的 upload.dir（默认 ./uploads/）下
 * `study-resource/yyyyMM/` 子目录，文件名用 UUID + 原扩展名，避免中文/重名问题。
 * 数据库 file_path 存相对可访问路径 `/uploads/study-resource/...`。
 */
@Service
public class StudyResourceServiceImpl implements StudyResourceService {

    @Autowired
    private StudyResourceMapper studyResourceMapper;

    @Autowired
    private CourseMemberMapper courseMemberMapper;

    /** 上传根目录，相对运行目录；通过 application.properties 的 upload.dir 配置 */
    @Value("${upload.dir:./uploads/}")
    private String uploadDir;

    /** 单文件大小上限，与 application.properties 的 spring.servlet.multipart.max-file-size 保持一致 */
    @Value("${spring.servlet.multipart.max-file-size:200MB}")
    private String maxUploadSize;

    /** 允许上传的文件扩展名白名单 */
    private static final Set<String> ALLOWED_EXT = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "txt", "md", "zip", "rar", "7z",
            "jpg", "jpeg", "png", "gif", "webp", "mp4", "mp3");

    /**
     * 外部链接资料的 file_type 标识：第三方教学视频等站外资源，
     * 链接地址存在 file_path 字段，不落盘、无文件大小。
     */
    private static final String FILE_TYPE_LINK = "link";

    @Override
    public PageResult<StudyResource> page(StudyResourceQuery query) {
        long total = studyResourceMapper.countPage(query);
        List<StudyResource> records = studyResourceMapper.selectPage(query);
        Long pn = query.getPageNum() == null ? 1L : query.getPageNum().longValue();
        Long ps = query.getPageSize() == null ? 10L : query.getPageSize().longValue();
        return PageResult.of(records, total, pn, ps);
    }

    @Override
    public List<StudyResource> listForExport(StudyResourceQuery query) {
        // 导出不受分页限制：直接放大 pageSize 查全量（可按筛选）
        StudyResourceQuery big = new StudyResourceQuery();
        big.setCourseId(query.getCourseId());
        big.setKeyword(query.getKeyword());
        // 数据权限必须一起带过来，否则导出会绕过「只导出自己任教课程资料」的限制
        big.setCourseIds(query.getCourseIds());
        big.setPageNum(1);
        big.setPageSize(100000);
        return studyResourceMapper.selectPage(big);
    }

    @Override
    public StudyResource upload(Long courseId, String resourceName, String description,
                                MultipartFile file, Long uploadTeacherId, String uploadTeacherName) {
        if (courseId == null) {
            throw new IllegalArgumentException("请选择所属课程");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String ext = resolveExt(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型（.pdf/.doc/.docx/.ppt/.pptx/.xls/.xlsx/.txt/.zip 等）");
        }
        long size = file.getSize();
        // 上限从配置读取（200MB），与 Spring multipart 限制保持一致，避免不同层限制不一致
        if (size > DataSize.parse(maxUploadSize).toBytes()) {
            throw new IllegalArgumentException("文件大小不能超过 " + maxUploadSize);
        }

        // 1. 落盘：study-resource/yyyyMM/uuid.ext
        //    注意：uploadDir 若为相对路径，依赖运行 CWD，Tomcat 部署时 CWD 是临时目录，
        //    容易导致目录落在不可靠路径。这里统一解析成绝对路径，并确保父目录存在。
        String relativeDir = "study-resource/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetDir = baseDir.resolve(relativeDir);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录：" + targetDir + "（" + e.getMessage() + "）", e);
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = targetDir.resolve(storedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败：" + e.getMessage(), e);
        }
        String filePath = "/uploads/" + relativeDir + "/" + storedName;

        // 2. 记录元数据
        StudyResource r = new StudyResource();
        r.setCourseId(courseId);
        r.setResourceName(resourceName != null && !resourceName.isBlank() ? resourceName.trim() : originalName);
        r.setFileType(resolveFileType(ext));
        r.setFilePath(filePath);
        r.setFileSize(size);
        r.setFileSizeText(formatSize(size));
        r.setUploadTeacherId(uploadTeacherId);
        r.setUploadTeacherName(uploadTeacherName);
        r.setDescription(description);
        studyResourceMapper.insert(r);
        return studyResourceMapper.selectById(r.getId());
    }

    @Override
    public void update(Long id, String resourceName, String description, Long courseId) {
        StudyResource exist = studyResourceMapper.selectById(id);
        if (exist == null) {
            throw new IllegalArgumentException("资料不存在或已删除");
        }
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("资料名称不能为空");
        }
        StudyResource r = new StudyResource();
        r.setId(id);
        r.setResourceName(resourceName.trim());
        r.setDescription(description);
        // courseId 为空则不改课程
        r.setCourseId(courseId);
        studyResourceMapper.update(r);
    }

    @Override
    public StudyResource addLink(Long courseId, String resourceName, String linkUrl, String description,
                                 Long uploadTeacherId, String uploadTeacherName) {
        if (courseId == null) {
            throw new IllegalArgumentException("请选择所属课程");
        }
        if (resourceName == null || resourceName.isBlank()) {
            throw new IllegalArgumentException("资料名称不能为空");
        }
        String url = normalizeLinkUrl(linkUrl);

        StudyResource r = new StudyResource();
        r.setCourseId(courseId);
        r.setResourceName(resourceName.trim());
        r.setFileType(FILE_TYPE_LINK);
        // 外部链接直接存 file_path（与文件资料共用字段），不落盘
        r.setFilePath(url);
        r.setFileSize(0L);
        // 链接没有文件大小，列表展示占位符，避免出现空白单元格
        r.setFileSizeText("-");
        r.setUploadTeacherId(uploadTeacherId);
        r.setUploadTeacherName(uploadTeacherName);
        r.setDescription(description);
        studyResourceMapper.insert(r);
        return studyResourceMapper.selectById(r.getId());
    }

    @Override
    public void updateLinkUrl(Long id, String linkUrl) {
        StudyResource exist = studyResourceMapper.selectById(id);
        if (exist == null) {
            throw new IllegalArgumentException("资料不存在或已删除");
        }
        if (!FILE_TYPE_LINK.equals(exist.getFileType())) {
            throw new IllegalArgumentException("该资料不是外部链接，不能修改链接地址");
        }
        studyResourceMapper.updateFilePath(id, normalizeLinkUrl(linkUrl));
    }

    /**
     * 校验并规范化外部链接地址。
     *
     * <p>只允许 http/https：避免 javascript: 等危险协议入库后，
     * 学生点击「去学习」时在前端执行脚本（存储型 XSS）；
     * 长度与 study_resource.file_path（VARCHAR(500)）保持一致。
     */
    private String normalizeLinkUrl(String linkUrl) {
        if (linkUrl == null || linkUrl.isBlank()) {
            throw new IllegalArgumentException("请填写链接地址");
        }
        String url = linkUrl.trim();
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("链接地址需以 http:// 或 https:// 开头");
        }
        if (url.length() > 500) {
            throw new IllegalArgumentException("链接地址过长（不超过 500 个字符）");
        }
        return url;
    }

    @Override
    public void delete(Long id) {
        StudyResource exist = studyResourceMapper.selectById(id);
        if (exist == null) {
            throw new IllegalArgumentException("资料不存在或已删除");
        }
        studyResourceMapper.deleteById(id);
        deletePhysicalFile(exist.getFilePath());
    }

    @Override
    public Map<String, Object> deleteBatch(List<Long> ids) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> failedReasons = new ArrayList<>();
        int success = 0;
        int failed = 0;
        if (ids == null || ids.isEmpty()) {
            result.put("success", 0);
            result.put("failed", 0);
            result.put("failedReasons", failedReasons);
            return result;
        }
        List<Long> uniqueIds = ids.stream().distinct().collect(java.util.stream.Collectors.toList());
        List<StudyResource> targets = studyResourceMapper.selectByIds(uniqueIds);
        Map<Long, StudyResource> map = new LinkedHashMap<>();
        for (StudyResource t : targets) {
            map.put(t.getId(), t);
        }
        List<Long> validIds = new ArrayList<>();
        for (Long id : uniqueIds) {
            StudyResource t = map.get(id);
            if (t == null) {
                failed++;
                failedReasons.add("资料ID=" + id + " 不存在或已删除，已跳过");
                continue;
            }
            validIds.add(id);
        }
        if (!validIds.isEmpty()) {
            studyResourceMapper.batchDelete(validIds);
            for (Long id : validIds) {
                StudyResource t = map.get(id);
                deletePhysicalFile(t.getFilePath());
            }
            success = validIds.size();
        }
        result.put("success", success);
        result.put("failed", failed);
        result.put("failedReasons", failedReasons);
        return result;
    }

    @Override
    public StudyResource getById(Long id) {
        return studyResourceMapper.selectById(id);
    }

    @Override
    public void incrementViewCount(Long id) {
        if (id == null) {
            return;
        }
        studyResourceMapper.increaseViewCount(id);
    }

    @Override
    public void incrementDownloadCount(Long id) {
        if (id == null) {
            return;
        }
        studyResourceMapper.increaseDownloadCount(id);
    }

    @Override
    public List<StudyResource> listByCourse(Long courseId) {
        if (courseId == null) {
            return List.of();
        }
        return studyResourceMapper.selectByCourse(courseId);
    }

    @Override
    public List<StudyResource> listByStudentJoined(Long studentId, Long courseId) {
        List<Long> joinedCourseIds = courseMemberMapper.selectJoinedCourseIds(studentId);
        if (joinedCourseIds.isEmpty()) {
            return List.of();
        }
        if (courseId != null) {
            // 只能查看自己已加入课程的资料
            if (!joinedCourseIds.contains(courseId)) {
                return List.of();
            }
            return studyResourceMapper.selectByCourse(courseId);
        }
        // 未选课程：返回全部已加入课程的资料
        return studyResourceMapper.selectByCourseIds(joinedCourseIds);
    }

    /** 删除物理文件：file_path 形如 /uploads/study-resource/...，去掉 /uploads/ 前缀后拼到上传根目录 */
    private void deletePhysicalFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        // 外部链接资料的 file_path 存的是站外 URL，没有服务器文件需要删除
        if (!filePath.startsWith("/uploads/")) {
            return;
        }
        String rel = filePath.substring("/uploads/".length());
        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path f = baseDir.resolve(rel);
        try {
            Files.deleteIfExists(f);
        } catch (IOException e) {
            // 删除物理文件失败不阻塞业务，仅记录（可选日志）
        }
    }

    /** 取扩展名（小写），无扩展名返回 "" */
    private String resolveExt(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx + 1).toLowerCase();
    }

    /** 由扩展名归类文件类型 */
    private String resolveFileType(String ext) {
        return switch (ext) {
            case "pdf" -> "pdf";
            case "doc", "docx" -> "word";
            case "ppt", "pptx" -> "ppt";
            case "xls", "xlsx" -> "excel";
            case "txt", "md" -> "text";
            case "zip", "rar", "7z" -> "compress";
            case "jpg", "jpeg", "png", "gif", "webp" -> "image";
            case "mp4" -> "video";
            case "mp3" -> "audio";
            default -> "other";
        };
    }

    /** 字节数 → 可读大小文本 */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.1f GB", gb);
    }
}
