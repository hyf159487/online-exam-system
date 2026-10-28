package com.exam.exam_system.service.impl;

import com.exam.exam_system.entity.Course;
import com.exam.exam_system.mapper.CourseMapper;
import com.exam.exam_system.service.CourseService;
import com.exam.exam_system.service.RedisCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 课程 Service 实现
 */
@Service
public class CourseServiceImpl implements CourseService {

    @Autowired
    private CourseMapper courseMapper;

    @Autowired(required = false)
    private RedisCacheService redisCacheService;

    /** 课程列表缓存 key（高频读取、低频写入） */
    private static final String COURSE_LIST_KEY = "cache:course:list";

    /** 课程列表缓存 TTL（秒）：10 分钟 */
    private static final long COURSE_LIST_TTL = 600L;

    /** 上传根目录（与学习资料共用 upload.dir 配置），课程封面存其下的 course-cover/yyyyMM/ */
    @Value("${upload.dir:./uploads/}")
    private String uploadDir;

    /** 封面允许的图片扩展名 */
    private static final Set<String> COVER_ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    /** 封面大小上限（5MB）：封面仅作展示，限制比资料文件严格 */
    private static final long COVER_MAX_SIZE = 5 * 1024 * 1024;

    @Override
    public List<Course> listAll() {
        // 优先读缓存
        if (redisCacheService != null) {
            List<Course> cached = redisCacheService.getObject(
                    COURSE_LIST_KEY, new TypeReference<List<Course>>() {});
            if (cached != null) {
                return cached;
            }
        }
        // 缓存未命中 → 查库并回填（含任课教师聚合）
        List<Course> list = courseMapper.selectAllWithTeachers();
        if (redisCacheService != null) {
            redisCacheService.setObject(COURSE_LIST_KEY, list, COURSE_LIST_TTL);
        }
        return list;
    }

    @Override
    public List<Course> search(String keyword, int limit) {
        String kw = (keyword == null) ? null : keyword.trim();
        if (kw != null && kw.isEmpty()) {
            kw = null;
        }
        if (limit <= 0) {
            limit = 20;
        }
        if (limit > 100) {
            limit = 100;
        }
        // 模糊搜索实时查库，不读 listAll 的 10 分钟缓存，保证搜到的都是最新数据
        return courseMapper.selectByKeyword(kw, limit);
    }

    /**
     * 上传课程封面图片（教师 / 管理员）
     *
     * <p>落盘到 uploadDir/course-cover/yyyyMM/uuid.ext，返回可访问路径
     * （/uploads/course-cover/...），前端保存课程时写入 cover 字段。
     */
    @Override
    public String uploadCover(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的封面图片");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        int dot = originalName.lastIndexOf('.');
        String ext = dot < 0 ? "" : originalName.substring(dot + 1).toLowerCase();
        if (!COVER_ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("封面仅支持 jpg/jpeg/png/gif/webp/bmp 格式的图片");
        }
        if (file.getSize() > COVER_MAX_SIZE) {
            throw new IllegalArgumentException("封面图片不能超过 5MB");
        }
        // 与学习资料一致：uploadDir 统一解析成绝对路径（Tomcat 部署时 CWD 是临时目录，相对路径不可靠）
        String relativeDir = "course-cover/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        Path baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path targetDir = baseDir.resolve(relativeDir);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录：" + targetDir + "（" + e.getMessage() + "）", e);
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetDir.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("封面保存失败：" + e.getMessage(), e);
        }
        return "/uploads/" + relativeDir + "/" + storedName;
    }

    @Override
    public Course create(Course course) {
        if (course.getCourseName() == null || course.getCourseName().isBlank()) {
            throw new IllegalArgumentException("课程名称不能为空");
        }
        String name = course.getCourseName().trim();
        if (courseMapper.selectByName(name) != null) {
            throw new IllegalArgumentException("课程「" + name + "」已存在");
        }
        // 课程编码唯一校验
        String code = course.getCourseCode() == null ? null : course.getCourseCode().trim();
        if (code != null && !code.isEmpty() && courseMapper.selectByCode(code) != null) {
            throw new IllegalArgumentException("课程编码「" + code + "」已被使用");
        }
        course.setCourseName(name);
        course.setCourseCode(code);
        // 封面：空串规整为 null，避免库里存空字符串（学生端按空值显示默认封面）
        if (course.getCover() != null && course.getCover().isBlank()) {
            course.setCover(null);
        }
        courseMapper.insert(course);
        bindTeachers(course.getId(), course.getTeacherIds());
        evictCourseListCache();
        return course;
    }

    @Override
    public void update(Course course) {
        if (course.getId() == null) {
            throw new IllegalArgumentException("课程 ID 不能为空");
        }
        if (course.getCourseName() == null || course.getCourseName().isBlank()) {
            throw new IllegalArgumentException("课程名称不能为空");
        }
        String name = course.getCourseName().trim();
        Course exist = courseMapper.selectByName(name);
        if (exist != null && !exist.getId().equals(course.getId())) {
            throw new IllegalArgumentException("课程「" + name + "」已存在");
        }
        // 课程编码唯一校验（排除自身）
        String code = course.getCourseCode() == null ? null : course.getCourseCode().trim();
        if (code != null && !code.isEmpty()) {
            Course codeExist = courseMapper.selectByCode(code);
            if (codeExist != null && !codeExist.getId().equals(course.getId())) {
                throw new IllegalArgumentException("课程编码「" + code + "」已被其他课程使用");
            }
        }
        course.setCourseName(name);
        course.setCourseCode(code);
        // 封面：null=本次不涉及该字段（保留库中原封面）；空串=显式清空封面
        if (course.getCover() == null) {
            Course current = courseMapper.selectById(course.getId());
            course.setCover(current == null ? null : current.getCover());
        } else if (course.getCover().isBlank()) {
            course.setCover(null);
        }
        int rows = courseMapper.update(course);
        if (rows == 0) {
            throw new IllegalArgumentException("课程不存在");
        }
        // 教师绑定：teacherIds 非 null 时全量重建（null 表示本次不涉及教师字段）
        if (course.getTeacherIds() != null) {
            courseMapper.deleteTeachers(course.getId());
            bindTeachers(course.getId(), course.getTeacherIds());
        }
        evictCourseListCache();
    }

    /**
     * 教师维护本人任教课程的信息（教师端「我的课程 → 课程信息」用）
     *
     * <p>与 {@link #update(Course)} 的差别：课程编码与任课教师绑定由管理员统一维护，
     * 教师提交的这两个字段在此被忽略，避免教师改编码破坏题库/试卷关联、或抢占他人课程。
     */
    @Override
    public void updateByTeacher(Course course) {
        if (course == null || course.getId() == null) {
            throw new IllegalArgumentException("课程 ID 不能为空");
        }
        if (course.getCourseName() == null || course.getCourseName().isBlank()) {
            throw new IllegalArgumentException("课程名称不能为空");
        }
        String name = course.getCourseName().trim();
        Course exist = courseMapper.selectByName(name);
        if (exist != null && !exist.getId().equals(course.getId())) {
            throw new IllegalArgumentException("课程「" + name + "」已存在");
        }
        course.setCourseName(name);
        // 教师端不参与课程编码与任课教师维护：置空后由 updateByTeacher 的 SQL 天然忽略
        course.setCourseCode(null);
        course.setTeacherIds(null);
        // 封面：null=本次不涉及该字段（保留库中原封面）；空串=显式清空封面
        if (course.getCover() == null) {
            Course current = courseMapper.selectById(course.getId());
            course.setCover(current == null ? null : current.getCover());
        } else if (course.getCover().isBlank()) {
            course.setCover(null);
        }
        int rows = courseMapper.updateByTeacher(course);
        if (rows == 0) {
            throw new IllegalArgumentException("课程不存在");
        }
        evictCourseListCache();
    }

    /** 绑定课程-教师关系（去重、忽略空） */
    private void bindTeachers(Long courseId, List<Long> teacherIds) {
        if (courseId == null || teacherIds == null) return;
        java.util.Set<Long> unique = new java.util.HashSet<>(teacherIds);
        for (Long tid : unique) {
            if (tid != null) {
                courseMapper.insertTeacher(courseId, tid);
            }
        }
    }

    @Override
    public void delete(Long id) {
        delete(id, false);
    }

    @Override
    public void delete(Long id, boolean force) {
        if (courseMapper.countQuestionRef(id) > 0) {
            throw new IllegalArgumentException("该课程已被试题引用，无法删除，请先解除关联试题");
        }
        if (courseMapper.countPaperRef(id) > 0) {
            throw new IllegalArgumentException("该课程已被试卷引用，无法删除，请先解除关联试卷");
        }
        if (courseMapper.countExamRef(id) > 0) {
            throw new IllegalArgumentException("该课程已被考试引用，无法删除，请先解除关联考试");
        }
        long kRef = courseMapper.countKnowledgeRef(id);
        // 非强制：存在知识点引用即拒绝，提示数量引导用户选择强制删除
        if (kRef > 0 && !force) {
            throw new IllegalArgumentException("该课程下仍有 " + kRef + " 个知识点，无法删除，请先移除知识点");
        }
        // 强制：无题目/试卷/考试引用时，知识点视为可清理的演示数据，连带删除后再删课程
        if (kRef > 0) {
            courseMapper.deleteKnowledgeByCourseId(id);
        }
        int rows = courseMapper.deleteById(id);
        if (rows == 0) {
            throw new IllegalArgumentException("课程不存在");
        }
        // 清理课程-教师关系
        courseMapper.deleteTeachers(id);
        evictCourseListCache();
    }

    @Override
    public java.util.Map<String, Object> getDetail(Long id) {
        Course course = courseMapper.selectByIdWithTeachers(id);
        if (course == null) {
            throw new IllegalArgumentException("课程不存在");
        }
        course.setTeacherIds(courseMapper.selectTeacherIds(id));
        java.util.Map<String, Object> detail = new java.util.LinkedHashMap<>();
        detail.put("course", course);
        detail.put("teachers", courseMapper.selectTeachers(id));
        detail.put("questionCount", courseMapper.countQuestionRef(id));
        detail.put("paperCount", courseMapper.countPaperRef(id));
        detail.put("examCount", courseMapper.countExamRef(id));
        detail.put("knowledgeCount", courseMapper.countKnowledgeRef(id));
        return detail;
    }

    @Override
    public java.util.Map<String, Object> importExcel(org.springframework.web.multipart.MultipartFile file) {
        java.util.List<String> failedReasons = new java.util.ArrayList<>();
        int success = 0;
        int failed = 0;

        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(file.getInputStream())) {
            org.apache.poi.xssf.usermodel.XSSFSheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            java.util.Set<String> seenCodes = new java.util.HashSet<>();

            for (int i = 1; i <= lastRow; i++) {
                org.apache.poi.xssf.usermodel.XSSFRow row = sheet.getRow(i);
                if (row == null) continue;
                String name = getCellString(row.getCell(0));
                String courseType = getCellString(row.getCell(1));
                if (courseType != null) courseType = courseType.trim();
                String code = getCellString(row.getCell(2));
                String desc = getCellString(row.getCell(3));
                int rowNum = i + 1;

                if (name == null || name.trim().isEmpty()) {
                    failed++;
                    failedReasons.add("第" + rowNum + "行：课程名称为空，已跳过");
                    continue;
                }
                name = name.trim();
                Course existing = courseMapper.selectByName(name);
                if (existing != null) {
                    Course incoming = new Course();
                    incoming.setId(existing.getId());
                    incoming.setCourseCode(code);
                    incoming.setCourseType(courseType);
                    incoming.setDescription(desc);
                    boolean filled = isBlank(existing.getCourseCode()) && !isBlank(code)
                            || isBlank(existing.getCourseType()) && !isBlank(courseType)
                            || isBlank(existing.getDescription()) && !isBlank(desc);
                    if (filled) {
                        courseMapper.fillEmptyFields(incoming);
                        success++;
                    } else {
                        failed++;
                        failedReasons.add("第" + rowNum + "行：课程「" + name + "」信息已完整，已跳过");
                    }
                    continue;
                }
                if (code != null && !code.trim().isEmpty()) {
                    code = code.trim();
                    if (!seenCodes.add(code) || courseMapper.selectByCode(code) != null) {
                        failed++;
                        failedReasons.add("第" + rowNum + "行：课程编码「" + code + "」重复，已跳过");
                        continue;
                    }
                } else {
                    code = null;
                }
                try {
                    Course c = new Course();
                    c.setCourseName(name);
                    c.setCourseType(courseType);
                    c.setCourseCode(code);
                    c.setDescription(desc);
                    courseMapper.insert(c);
                    success++;
                } catch (Exception e) {
                    failed++;
                    failedReasons.add("第" + rowNum + "行：课程「" + name + "」保存失败：" + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel 解析失败：" + e.getMessage(), e);
        }

        if (success > 0) {
            evictCourseListCache();
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("failedReasons", failedReasons);
        return result;
    }

    /** 判断文本是否为空或仅包含空白字符 */
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** 读取 Excel 单元格为字符串 */
    private String getCellString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING: return cell.getStringCellValue().trim();
            case NUMERIC:
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d)) return String.valueOf((long) d);
                return String.valueOf(d);
            case BOOLEAN: return String.valueOf(cell.getBooleanCellValue());
            default: return null;
        }
    }

    /** 清除课程列表缓存 */
    private void evictCourseListCache() {
        if (redisCacheService != null) {
            redisCacheService.delete(COURSE_LIST_KEY);
        }
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public java.util.Map<String, Object> assignTeachersRandomly(java.util.List<Long> courseIds,
                                                                int minCount, int maxCount,
                                                                boolean skipBound) {
        // 1. 教师池：全部启用教师
        java.util.List<Long> pool = courseMapper.selectAllTeacherIds();
        if (pool == null || pool.isEmpty()) {
            throw new IllegalArgumentException("暂无可用的教师账号，请先在「账号管理」中新增教师");
        }

        // 2. 目标课程：指定 id 列表，或全部课程
        java.util.List<Course> targets;
        if (courseIds == null || courseIds.isEmpty()) {
            targets = courseMapper.selectAll();
        } else {
            targets = courseMapper.selectByIds(courseIds.stream().distinct()
                    .collect(java.util.stream.Collectors.toList()));
        }
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("没有可分配的课程");
        }

        // 3. 教师人数上下限收敛到 [1, 教师池大小]
        int min = Math.max(1, minCount);
        int max = Math.max(min, maxCount);
        if (max > pool.size()) {
            max = pool.size();
            min = Math.min(min, max);
        }

        java.util.Random random = new java.util.Random();
        java.util.List<String> failedReasons = new java.util.ArrayList<>();
        int success = 0;
        int skipped = 0;
        int failed = 0;

        for (Course c : targets) {
            try {
                java.util.List<Long> current = courseMapper.selectTeacherIds(c.getId());
                if (skipBound && current != null && !current.isEmpty()) {
                    skipped++;
                    continue;
                }
                // 随机取 n 名教师（min~max）
                int n = min + random.nextInt(max - min + 1);
                java.util.List<Long> shuffled = new java.util.ArrayList<>(pool);
                java.util.Collections.shuffle(shuffled, random);
                java.util.List<Long> picked = shuffled.subList(0, n);

                // 全量重建绑定
                courseMapper.deleteTeachers(c.getId());
                for (Long tid : picked) {
                    courseMapper.insertTeacher(c.getId(), tid);
                }
                success++;
            } catch (Exception ex) {
                failed++;
                failedReasons.add("课程「" + c.getCourseName() + "」分配失败：" + ex.getMessage());
            }
        }

        if (success > 0) {
            evictCourseListCache();
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", targets.size());
        result.put("success", success);
        result.put("skipped", skipped);
        result.put("failed", failed);
        result.put("teacherCount", pool.size());
        result.put("failedReasons", failedReasons);
        return result;
    }

    @Override
    public java.util.Map<String, Object> deleteBatch(java.util.List<Long> ids) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        java.util.List<String> failedReasons = new java.util.ArrayList<>();
        int success = 0;
        int failed = 0;

        if (ids == null || ids.isEmpty()) {
            result.put("success", 0);
            result.put("failed", 0);
            result.put("failedReasons", failedReasons);
            return result;
        }
        java.util.List<Long> uniqueIds = ids.stream().distinct().collect(java.util.stream.Collectors.toList());
        java.util.List<Course> targets = courseMapper.selectByIds(uniqueIds);
        java.util.Map<Long, Course> courseMap = new java.util.HashMap<>();
        for (Course c : targets) courseMap.put(c.getId(), c);

        for (Long id : uniqueIds) {
            Course c = courseMap.get(id);
            if (c == null) {
                failed++;
                failedReasons.add("课程ID=" + id + " 不存在，已跳过");
                continue;
            }
            String name = c.getCourseName();
            // 按引用强度序检查（试题 > 试卷 > 考试 > 知识点），一次只报最相关原因
            long qRef = courseMapper.countQuestionRef(id);
            if (qRef > 0) {
                failed++;
                failedReasons.add("课程「" + name + "」已被 " + qRef + " 道试题引用，无法删除");
                continue;
            }
            long pRef = courseMapper.countPaperRef(id);
            if (pRef > 0) {
                failed++;
                failedReasons.add("课程「" + name + "」已被 " + pRef + " 份试卷引用，无法删除");
                continue;
            }
            long eRef = courseMapper.countExamRef(id);
            if (eRef > 0) {
                failed++;
                failedReasons.add("课程「" + name + "」已被 " + eRef + " 场考试引用，无法删除");
                continue;
            }
            long kRef = courseMapper.countKnowledgeRef(id);
            if (kRef > 0) {
                failed++;
                failedReasons.add("课程「" + name + "」仍有 " + kRef + " 个知识点，无法删除");
                continue;
            }
            // 无引用：真删 + 清理课程-教师关系
            try {
                courseMapper.deleteById(id);
                courseMapper.deleteTeachers(id);
                success++;
            } catch (Exception ex) {
                failed++;
                failedReasons.add("课程「" + name + "」删除失败：" + ex.getMessage());
            }
        }

        if (success > 0) {
            evictCourseListCache();
        }
        result.put("success", success);
        result.put("failed", failed);
        result.put("failedReasons", failedReasons);
        return result;
    }
}
