package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.BizException;
import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.PracticeAttachment;
import com.exam.exam_system.dto.PracticePaperSaveRequest;
import com.exam.exam_system.dto.PracticeReviewItem;
import com.exam.exam_system.dto.RandomComposeRequest;
import com.exam.exam_system.entity.*;
import com.exam.exam_system.mapper.*;
import com.exam.exam_system.service.PracticePaperService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 练习卷管理服务实现
 */
@Service
public class PracticePaperServiceImpl implements PracticePaperService {

    @Autowired
    private PracticePaperMapper paperMapper;

    @Autowired
    private PracticePaperQuestionMapper paperQuestionMapper;

    @Autowired
    private PracticeRecordMapper recordMapper;

    @Autowired
    private PracticeAnswerMapper answerMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private QuestionMapper questionMapper;

    /** 上传根目录，相对运行目录；通过 application.properties 的 upload.dir 配置 */
    @Value("${upload.dir:./uploads/}")
    private String uploadDir;

    /** 单文件大小上限，与 application.properties 的 spring.servlet.multipart.max-file-size 保持一致 */
    @Value("${spring.servlet.multipart.max-file-size:300MB}")
    private String maxUploadSize;

    /** 作业附件存放子目录（对外访问路径 /uploads/homework/...） */
    private static final String ATTACHMENT_DIR = "homework";

    /** 作业附件访问路径前缀：保存时校验，避免前端传入任意路径 */
    private static final String ATTACHMENT_PATH_PREFIX = "/uploads/homework/";

    /** 作业附件允许的扩展名白名单 */
    private static final Set<String> ALLOWED_ATTACHMENT_EXT = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "txt", "md", "zip", "rar", "7z",
            "jpg", "jpeg", "png", "gif", "webp");

    // ================= 练习卷 =================

    @Override
    public PageResult<PracticePaper> listPaper(int pageNum, int pageSize, Long teacherId, Integer role,
                                               String keyword, Long courseId, Integer status, Long studentId) {
        int offset = (pageNum - 1) * pageSize;
        List<PracticePaper> list = paperMapper.listPage(offset, pageSize, teacherId, role, keyword, courseId, status, studentId);
        long total = paperMapper.countPage(teacherId, role, keyword, courseId, status, null);
        return PageResult.of(list, total, (long) pageNum, (long) pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long savePaper(PracticePaperSaveRequest request, Long teacherId, String teacherName, Integer role) {
        if (request.getPaperName() == null || request.getPaperName().trim().isEmpty()) {
            throw new RuntimeException("作业名称不能为空");
        }
        // 作业内容两种来源：①从题库选题 ②上传作业文件；两者至少要有一种
        List<PracticePaperQuestion> questions = request.getQuestions() == null
                ? List.of() : request.getQuestions();
        String attachmentPath = normalizeAttachmentPath(request.getAttachmentPath());
        if (questions.isEmpty() && attachmentPath == null) {
            throw new RuntimeException("请至少选择题目或上传作业文件");
        }

        int totalScore = 0;
        for (int i = 0; i < questions.size(); i++) {
            PracticePaperQuestion q = questions.get(i);
            if (q.getSortNo() == null) q.setSortNo(i + 1);
            if (q.getScore() == null) q.setScore(0);
            totalScore += q.getScore();
        }

        PracticePaper paper = new PracticePaper();
        paper.setPaperName(request.getPaperName().trim());
        paper.setCourseId(request.getCourseId());
        paper.setDescription(request.getDescription());
        paper.setTotalScore(totalScore);
        paper.setQuestionCount(questions.size());
        // 文件型作业：附件为空即视为清空附件（编辑时从「上传文件」切回「题库选题」的场景）
        if (attachmentPath != null) {
            paper.setAttachmentName(resolveAttachmentName(request.getAttachmentName(), attachmentPath));
            paper.setAttachmentPath(attachmentPath);
            paper.setAttachmentSize(request.getAttachmentSize());
            paper.setAttachmentType(request.getAttachmentType());
        }

        Integer targetStatus = request.getTargetStatus();
        if (targetStatus == null || (targetStatus != 0 && targetStatus != 1)) {
            targetStatus = 0; // 默认草稿
        }

        if (request.getId() == null) {
            // 新增
            paper.setStatus(targetStatus);
            paper.setCreateTeacherId(teacherId);
            paper.setCreateTeacherName(resolveTeacherName(teacherId, teacherName));
            paperMapper.insert(paper);
        } else {
            // 编辑：先查权限
            PracticePaper exist = paperMapper.selectById(request.getId());
            if (exist == null) throw new RuntimeException("练习卷不存在或已删除");
            if (role != null && role == 1 && !exist.getCreateTeacherId().equals(teacherId)) {
                throw new RuntimeException("无权编辑他人创建的练习卷");
            }
            // 有练习记录时修改题目发出警告（但不阻断，毕设简化，允许修改）
            paper.setId(request.getId());
            paper.setStatus(exist.getStatus()); // 编辑不改状态，单独走发布/停用接口
            paperMapper.updateById(paper);

            // 先删旧关联，再插新关联
            paperQuestionMapper.deleteByPaperId(request.getId());
        }

        // 批量插入题目关联（文件型作业没有题目，跳过）
        if (!questions.isEmpty()) {
            paperQuestionMapper.batchInsert(paper.getId(), questions);
        }

        // 再次刷新总分/题数（文件型作业为 0）
        paperMapper.updateById(paper);

        return paper.getId();
    }

    private String resolveTeacherName(Long teacherId, String fallback) {
        if (fallback != null && !fallback.isEmpty()) return fallback;
        SysUser u = sysUserMapper.selectById(teacherId);
        if (u == null) return "教师";
        return (u.getRealName() != null && !u.getRealName().isEmpty()) ? u.getRealName() : u.getUsername();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, Integer targetStatus, Long teacherId, Integer role) {
        if (targetStatus == null || (targetStatus != 1 && targetStatus != 2)) {
            throw new RuntimeException("不支持的状态");
        }
        PracticePaper exist = paperMapper.selectById(id);
        if (exist == null) throw new RuntimeException("练习卷不存在或已删除");
        if (role != null && role == 1) {
            // 教师：只能操作自己创建的
            if (!exist.getCreateTeacherId().equals(teacherId)) {
                throw new RuntimeException("无权操作他人创建的练习卷");
            }
        }
        // 发布前必须有作业内容：题库选题（至少一题）或 上传作业文件
        boolean hasQuestions = exist.getQuestionCount() != null && exist.getQuestionCount() > 0;
        boolean hasAttachment = exist.getAttachmentPath() != null && !exist.getAttachmentPath().isBlank();
        if (targetStatus == 1 && !hasQuestions && !hasAttachment) {
            throw new RuntimeException("作业为空，请先选择题目或上传作业文件后再发布");
        }
        paperMapper.updateStatus(id, targetStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePaper(Long id, Long teacherId, Integer role) {
        PracticePaper exist = paperMapper.selectById(id);
        if (exist == null) throw new RuntimeException("练习卷不存在或已删除");
        if (role != null && role == 1) {
            if (!exist.getCreateTeacherId().equals(teacherId)) {
                throw new RuntimeException("无权删除他人创建的练习卷");
            }
        }
        // 删题目关联（主表逻辑删除）
        paperQuestionMapper.deleteByPaperId(id);
        paperMapper.logicDelete(id);
    }

    @Override
    public PracticePaper detail(Long id) {
        PracticePaper paper = paperMapper.selectById(id);
        if (paper != null) {
            paper.setQuestions(paperQuestionMapper.listByPaperId(id));
        }
        return paper;
    }

    // ================= 练习记录 =================

    @Override
    public PageResult<PracticeRecord> listRecord(int pageNum, int pageSize,
                                                 Long teacherId, Integer role,
                                                 Long paperId, Long studentId, String keyword) {
        int offset = (pageNum - 1) * pageSize;
        List<PracticeRecord> list = recordMapper.listPage(offset, pageSize, teacherId, role, paperId, studentId, keyword, null);
        long total = recordMapper.countPage(teacherId, role, paperId, studentId, keyword, null);
        return PageResult.of(list, total, (long) pageNum, (long) pageSize);
    }

    @Override
    public List<PracticeAnswer> recordDetail(Long recordId, Long teacherId, Integer role) {
        PracticeRecord record = recordMapper.selectById(recordId);
        if (record == null) throw new RuntimeException("练习记录不存在");
        // 权限：教师只能看自己练习卷的记录
        PracticePaper paper = paperMapper.selectById(record.getPracticePaperId());
        if (paper == null) throw new RuntimeException("练习卷不存在或已删除");
        if (role != null && role == 1) {
            if (!paper.getCreateTeacherId().equals(teacherId)) {
                throw new RuntimeException("无权查看他人练习卷的答题详情");
            }
        }
        return answerMapper.listByRecordId(recordId);
    }

    // ================= 批改作业 =================

    @Override
    public PageResult<PracticeRecord> listReview(int pageNum, int pageSize,
                                                 Long teacherId, Integer role,
                                                 Long paperId, String keyword, Integer reviewStatus) {
        int offset = (pageNum - 1) * pageSize;
        List<PracticeRecord> list = recordMapper.listPage(offset, pageSize, teacherId, role,
                paperId, null, keyword, reviewStatus);
        long total = recordMapper.countPage(teacherId, role, paperId, null, keyword, reviewStatus);
        return PageResult.of(list, total, (long) pageNum, (long) pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReview(Long recordId, List<PracticeReviewItem> items, Long teacherId, Integer role) {
        PracticeRecord record = recordMapper.selectById(recordId);
        if (record == null) throw new RuntimeException("作业记录不存在");
        PracticePaper paper = paperMapper.selectById(record.getPracticePaperId());
        if (paper == null) throw new RuntimeException("作业不存在或已删除");
        if (role != null && role == 1) {
            if (!paper.getCreateTeacherId().equals(teacherId)) {
                throw new RuntimeException("无权批改他人发布的作业");
            }
        }

        List<PracticeAnswer> answers = answerMapper.listByRecordId(recordId);
        if (answers == null || answers.isEmpty()) throw new RuntimeException("该记录没有作答明细");
        // 只有主观题（简答题/名词解释）需要教师批改
        Map<Long, PracticeAnswer> subjectiveMap = new HashMap<>();
        for (PracticeAnswer a : answers) {
            if (a.getIsObjective() != null && a.getIsObjective() == 0) {
                subjectiveMap.put(a.getId(), a);
            }
        }
        if (subjectiveMap.isEmpty()) throw new RuntimeException("该作业没有需要批改的主观题");
        if (items == null || items.isEmpty()) throw new RuntimeException("请至少批改一道主观题");

        for (PracticeReviewItem item : items) {
            if (item == null || item.getAnswerId() == null) continue;
            PracticeAnswer target = subjectiveMap.get(item.getAnswerId());
            if (target == null) throw new RuntimeException("只能批改本次作业的主观题");
            int full = target.getScore() == null ? 0 : target.getScore();
            int reviewScore = item.getReviewScore() == null ? 0 : item.getReviewScore();
            if (reviewScore < 0) reviewScore = 0;
            if (reviewScore > full) {
                throw new RuntimeException("第 " + target.getSortNo() + " 题得分不能超过满分 " + full + " 分");
            }
            String opinion = item.getReviewOpinion();
            if (opinion != null && opinion.length() > 255) opinion = opinion.substring(0, 255);
            answerMapper.updateReview(target.getId(), reviewScore, opinion, teacherId);
        }

        // 重算本次作业总分与对错题数：客观题按提交时的判分，主观题按批改结果
        int score = 0, correctCount = 0, wrongCount = 0, pendingCount = 0;
        for (PracticeAnswer a : answerMapper.listByRecordId(recordId)) {
            boolean subjective = a.getIsObjective() != null && a.getIsObjective() == 0;
            if (subjective) {
                if (a.getReviewStatus() != null && a.getReviewStatus() == 1 && a.getReviewScore() != null) {
                    score += a.getReviewScore();
                    if (a.getReviewScore() > 0) correctCount++; else wrongCount++;
                } else {
                    pendingCount++;
                }
            } else {
                score += a.getGetScore() == null ? 0 : a.getGetScore();
                if (a.getIsCorrect() != null && a.getIsCorrect() == 1) correctCount++; else wrongCount++;
            }
        }
        // 批改完就标记为已批改，仍有主观题未批改则保持待批改
        recordMapper.updateScoreReview(recordId, score, correctCount, wrongCount, pendingCount > 0 ? 1 : 2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewAttachmentSubmit(Long recordId, Integer score, String opinion, Long teacherId, Integer role) {
        PracticeRecord record = recordMapper.selectById(recordId);
        if (record == null) throw new RuntimeException("作业记录不存在");
        // 文件型提交：记录上挂着学生提交的附件
        if (record.getAttachmentPath() == null || record.getAttachmentPath().isBlank()) {
            throw new RuntimeException("该记录不是文件型作业提交，请按答题明细批改");
        }
        PracticePaper paper = paperMapper.selectById(record.getPracticePaperId());
        if (paper == null) throw new RuntimeException("作业不存在或已删除");
        if (role != null && role == 1 && !paper.getCreateTeacherId().equals(teacherId)) {
            throw new RuntimeException("无权批改他人发布的作业");
        }
        // 文件型作业没有题目分值：作业设了总分就用总分做上限，否则按 100 分制
        int max = (paper.getTotalScore() != null && paper.getTotalScore() > 0) ? paper.getTotalScore() : 100;
        int s = score == null ? 0 : score;
        if (s < 0) s = 0;
        if (s > max) throw new RuntimeException("得分不能超过满分 " + max + " 分");
        String op = opinion;
        if (op != null && op.length() > 255) op = op.substring(0, 255);
        recordMapper.updateAttachmentReview(recordId, s, op, teacherId);
    }

    // ================= 作业随机选题 =================

    @Override
    public List<Question> pickRandom(Long courseId, List<RandomComposeRequest.Rule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new BizException("请至少配置一条抽题规则");
        }
        if (courseId == null) {
            throw new BizException("请先为作业选择所属课程后再进行随机选题");
        }

        // 1. 先校验各规则的题库数量，一次性收集不足项，供前端"自动调整/前往添加题目"使用
        List<Map<String, Object>> insufficient = new ArrayList<>();
        for (RandomComposeRequest.Rule rule : rules) {
            Integer questionType = rule.getQuestionType();
            Integer count = rule.getCount();
            if (questionType == null || count == null || count <= 0) {
                throw new BizException("抽题规则必须指定题型且数量大于0");
            }
            long available = questionMapper.countByTypeAndDifficulty(courseId, questionType, rule.getDifficulty());
            if (available < count) {
                Map<String, Object> item = new HashMap<>();
                item.put("questionType", questionType);
                item.put("need", count);
                item.put("available", available);
                item.put("difficulty", rule.getDifficulty());
                insufficient.add(item);
            }
        }
        if (!insufficient.isEmpty()) {
            String detail = insufficient.stream()
                    .map(m -> "题型[" + m.get("questionType") + "]题库不足：需要" + m.get("need")
                            + "题，可用" + m.get("available") + "题")
                    .collect(Collectors.joining("；"));
            throw new BizException(detail, Map.of("insufficient", insufficient));
        }

        // 2. 全部校验通过后逐条规则抽题，汇总返回（不落库）
        List<Question> result = new ArrayList<>();
        for (RandomComposeRequest.Rule rule : rules) {
            List<Question> picked = questionMapper.randomPick(
                    courseId, rule.getQuestionType(), rule.getDifficulty(), rule.getCount());
            result.addAll(picked);
        }
        return result;
    }

    // ================= 文件型作业：附件上传 =================

    @Override
    public PracticeAttachment uploadAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的作业文件");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String ext = resolveExt(originalName);
        if (!ALLOWED_ATTACHMENT_EXT.contains(ext)) {
            throw new IllegalArgumentException(
                    "不支持的作业文件类型（支持 .pdf/.doc/.docx/.ppt/.pptx/.xls/.xlsx/.txt/.zip/.rar/.jpg/.png 等）");
        }
        long size = file.getSize();
        if (size > DataSize.parse(maxUploadSize).toBytes()) {
            throw new IllegalArgumentException("文件大小不能超过 " + maxUploadSize);
        }

        // 落盘：homework/yyyyMM/uuid.ext
        // 与学习资料一致：uploadDir 统一解析成绝对路径（Tomcat 部署时 CWD 不可靠），
        // 文件名用 UUID + 原扩展名，避免中文/重名问题。
        String relativeDir = ATTACHMENT_DIR + "/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
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
            throw new IllegalStateException("作业文件保存失败：" + e.getMessage(), e);
        }

        PracticeAttachment dto = new PracticeAttachment();
        dto.setFileName(originalName);
        dto.setFilePath("/uploads/" + relativeDir + "/" + storedName);
        dto.setFileSize(size);
        dto.setFileSizeText(formatSize(size));
        dto.setFileType(resolveAttachmentType(ext));
        return dto;
    }

    /**
     * 校验附件访问路径：只接受本系统上传接口产出的 /uploads/homework/xxx，
     * 防止前端伪造任意路径（如 javascript: / 站外地址）写入库后在前端执行。
     *
     * @return 合法路径；空值返回 null（表示不挂附件 / 清空附件）
     */
    private String normalizeAttachmentPath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String p = path.trim();
        if (p.length() > 500) {
            throw new RuntimeException("作业附件地址过长，请重新上传");
        }
        if (!p.startsWith(ATTACHMENT_PATH_PREFIX) || p.contains("..")) {
            throw new RuntimeException("作业附件地址不合法，请重新上传");
        }
        return p;
    }

    /** 附件展示名：优先用前端回传的原始文件名，缺失时退化为存储文件名 */
    private String resolveAttachmentName(String name, String path) {
        String n = (name == null || name.isBlank()) ? path.substring(path.lastIndexOf('/') + 1) : name.trim();
        return n.length() > 255 ? n.substring(0, 255) : n;
    }

    /** 取扩展名（小写），无扩展名返回 "" */
    private String resolveExt(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx + 1).toLowerCase();
    }

    /** 由扩展名归类附件类型（前端据此决定预览/下载方式） */
    private String resolveAttachmentType(String ext) {
        return switch (ext) {
            case "pdf" -> "pdf";
            case "doc", "docx" -> "word";
            case "ppt", "pptx" -> "ppt";
            case "xls", "xlsx" -> "excel";
            case "txt", "md" -> "text";
            case "zip", "rar", "7z" -> "compress";
            case "jpg", "jpeg", "png", "gif", "webp" -> "image";
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

    // ================= 文件型作业：教师下载学生提交的文件 =================

    @Override
    public PracticeRecord loadSubmittedAttachment(Long recordId, Long teacherId, Integer role) {
        if (recordId == null) throw new RuntimeException("作业记录不存在");
        PracticeRecord record = recordMapper.selectById(recordId);
        if (record == null) throw new RuntimeException("作业记录不存在");
        // 只有文件型提交（学生上传了作业文件）才可下载
        if (record.getAttachmentPath() == null || record.getAttachmentPath().isBlank()) {
            throw new RuntimeException("该记录没有学生提交的作业文件");
        }
        PracticePaper paper = paperMapper.selectById(record.getPracticePaperId());
        if (paper == null) throw new RuntimeException("作业不存在或已删除");
        // 数据权限：教师只能下载自己发布作业的提交（管理员放行）
        if (role != null && role == 1
                && (paper.getCreateTeacherId() == null || !paper.getCreateTeacherId().equals(teacherId))) {
            throw new RuntimeException("无权下载他人发布作业的提交");
        }
        return record;
    }
}
