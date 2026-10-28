package com.exam.exam_system.service.impl;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.UserQuery;
import com.exam.exam_system.entity.SysUser;
import com.exam.exam_system.mapper.SysClassMapper;
import com.exam.exam_system.mapper.SysUserMapper;
import com.exam.exam_system.service.RedisCacheService;
import com.exam.exam_system.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private SysClassMapper sysClassMapper;

    /** 由 SecurityConfig 注入的 BCrypt 加密器，用来比对密码 */
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired(required = false)
    private RedisCacheService redisCacheService;

    /** 头像上传根目录（与 WebConfig 的 /uploads/** 静态映射保持一致） */
    @Value("${upload.dir:./uploads/}")
    private String uploadDir;

    /** 头像允许的图片扩展名 */
    private static final Set<String> AVATAR_ALLOWED_EXT =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");

    /** 头像大小上限：2MB（头像不需要太大，顺带避免占用磁盘） */
    private static final long AVATAR_MAX_SIZE = 2L * 1024 * 1024;

    /** 登录 token 缓存 key 前缀（与 AuthController 保持一致） */
    private static final String TOKEN_KEY_PREFIX = "auth:token:";

    @Override
    public SysUser login(String username, String password) {
        if (username == null || username.isEmpty()
                || password == null || password.isEmpty()) {
            return null;
        }
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            return null;                       // 用户不存在
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return null;                       // 账号被禁用
        }
        // BCrypt 验密：明文 → 哈希比对
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return null;                       // 密码错误
        }
        return user;
    }

    @Override
    public List<SysUser> listByRole(Integer role) {
        return userMapper.listByRole(role);
    }

    @Override
    public long countByRole(Integer role) {
        return userMapper.countByRole(role);
    }

    @Override
    public PageResult<SysUser> page(UserQuery query) {
        long total = userMapper.countByQuery(query);
        List<SysUser> records = userMapper.listByQuery(query);
        return PageResult.of(records, total,
                query.getPageNum() == null ? 1L : query.getPageNum().longValue(),
                query.getPageSize() == null ? 10L : query.getPageSize().longValue());
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        int rows = userMapper.updateStatus(id, status);
        if (rows == 0) {
            throw new IllegalArgumentException("用户不存在或状态无变化");
        }
        // 禁用账号时删除其登录 token 缓存，实现「强制下线」（Redis 可用时生效）
        if (status != null && status == 0 && redisCacheService != null) {
            redisCacheService.delete(TOKEN_KEY_PREFIX + id);
        }
    }

    @Override
    public void updatePassword(Long id, String bcryptPassword) {
        int rows = userMapper.updatePassword(id, bcryptPassword);
        if (rows == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    @Override
    public void updateClassId(Long id, Long classId) {
        int rows = userMapper.updateClassId(id, classId);
        if (rows == 0) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    @Override
    public int updateClassBatch(List<Long> studentIds, Long targetClassId) {
        // 边界校验：批量调班参数不能为空
        if (studentIds == null || studentIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一名学生");
        }
        if (targetClassId == null) {
            throw new IllegalArgumentException("请选择目标班级");
        }
        // 目标班级必须真实存在，避免产生学生关联无效班级 id 的脏数据
        if (sysClassMapper.selectById(targetClassId) == null) {
            throw new IllegalArgumentException("目标班级不存在");
        }
        int rows = userMapper.updateClassBatch(studentIds, targetClassId);
        if (rows == 0) {
            throw new IllegalArgumentException("没有学生被迁移：所选账号可能不是学生、不存在或已在目标班级");
        }
        return rows;
    }

    @Override
    public int removeClassBatch(List<Long> studentIds) {
        // 边界校验：批量移出参数不能为空
        if (studentIds == null || studentIds.isEmpty()) {
            throw new IllegalArgumentException("请至少选择一名学生");
        }
        int rows = userMapper.clearClassBatch(studentIds);
        if (rows == 0) {
            throw new IllegalArgumentException("没有学生被移出：所选账号可能不是学生、不存在或尚未分配班级");
        }
        return rows;
    }

    @Override
    public List<SysUser> listByClassId(Long classId) {
        if (classId == null) {
            throw new IllegalArgumentException("classId 不能为空");
        }
        List<SysUser> students = userMapper.selectByClassId(classId);
        // 脱敏：不回传密码哈希
        students.forEach(u -> u.setPassword(null));
        return students;
    }

    @Override
    public SysUser createUser(SysUser user) {
        // 账号唯一性校验
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            throw new IllegalArgumentException("账号「" + user.getUsername() + "」已存在");
        }
        // 密码处理：留空默认 123456，统一 BCrypt 哈希
        String rawPwd = (user.getPassword() == null || user.getPassword().isEmpty())
                ? "123456" : user.getPassword();
        user.setPassword(passwordEncoder.encode(rawPwd));
        // 状态默认启用
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        userMapper.insert(user);
        return user;
    }

    @Override
    public java.util.Map<String, Integer> createBatch(java.util.List<SysUser> users) {
        int success = 0;
        int skipped = 0;
        for (SysUser u : users) {
            // 字段缺失或账号重复 → 跳过该条，不影响其余
            if (u.getUsername() == null || u.getUsername().trim().isEmpty()
                    || u.getRealName() == null || u.getRealName().trim().isEmpty()
                    || u.getRole() == null
                    || userMapper.selectByUsername(u.getUsername().trim()) != null) {
                skipped++;
                continue;
            }
            try {
                u.setUsername(u.getUsername().trim());
                u.setRealName(u.getRealName().trim());
                createUser(u);
                success++;
            } catch (Exception e) {
                skipped++;
            }
        }
        java.util.Map<String, Integer> result = new java.util.HashMap<>();
        result.put("success", success);
        result.put("skipped", skipped);
        return result;
    }

    @Override
    public java.util.Map<String, Object> deleteBatch(java.util.List<Long> ids) {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        java.util.List<String> skippedReasons = new java.util.ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            result.put("success", 0);
            result.put("skipped", 0);
            result.put("skippedReasons", skippedReasons);
            return result;
        }

        // 去重，避免同一 id 重复处理
        java.util.List<Long> uniqueIds = ids.stream().distinct().collect(java.util.stream.Collectors.toList());

        // 管理员账号保护：系统至少保留一个管理员（部分失败策略，跳过而不整批失败）
        java.util.List<com.exam.exam_system.entity.SysUser> targets = userMapper.selectByIds(uniqueIds);
        java.util.List<Long> adminIds = targets.stream()
                .filter(u -> u.getRole() != null && u.getRole() == 2)
                .map(com.exam.exam_system.entity.SysUser::getId)
                .collect(java.util.stream.Collectors.toList());
        if (!adminIds.isEmpty()) {
            long adminTotal = userMapper.countByRole(2);
            if (adminTotal - adminIds.size() < 1) {
                // 本次全删会导致系统没有任何管理员 → 管理员账号全部跳过，仅删除非管理员
                uniqueIds.removeAll(adminIds);
                for (Long adminId : adminIds) {
                    targets.stream()
                            .filter(u -> u.getId().equals(adminId))
                            .findFirst()
                            .ifPresent(u -> skippedReasons.add("账号「" + u.getRealName() + "(" + u.getUsername()
                                    + ")」是系统最后一个管理员，至少需保留一个管理员账号，已跳过"));
                }
            }
        }

        // 全部被跳过（如只勾了最后一个管理员）→ 无需执行删除
        if (uniqueIds.isEmpty()) {
            result.put("success", 0);
            result.put("skipped", skippedReasons.size());
            result.put("skippedReasons", skippedReasons);
            return result;
        }

        // admin 超级管理员保护在 SQL 层完成（username != 'admin'），
        // 若传入了 admin 的 id，真实删除数会少于预期，这里补充跳过原因
        int deleted = userMapper.deleteByIds(uniqueIds);
        int skipped = uniqueIds.size() - deleted;
        if (skipped > 0) {
            for (Long id : uniqueIds) {
                targets.stream()
                        .filter(u -> u.getId().equals(id) && "admin".equals(u.getUsername()))
                        .findFirst()
                        .ifPresent(u -> skippedReasons.add("超级管理员 admin 账号受系统保护，无法删除"));
            }
        }

        result.put("success", deleted);
        result.put("skipped", skippedReasons.size());
        result.put("skippedReasons", skippedReasons);
        return result;
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

            // 班级名→id 缓存（避免逐行查库）
            java.util.Map<String, Long> classMap = new java.util.HashMap<>();

            for (int i = 1; i <= lastRow; i++) {
                org.apache.poi.xssf.usermodel.XSSFRow row = sheet.getRow(i);
                if (row == null) continue;

                String username = getCellString(row.getCell(0));
                String realName = getCellString(row.getCell(1));
                String roleStr = getCellString(row.getCell(2));
                String className = getCellString(row.getCell(3));
                String phone = getCellString(row.getCell(4));
                String email = getCellString(row.getCell(5));

                int rowNum = i + 1;

                if (username == null || username.trim().isEmpty()) {
                    failed++;
                    failedReasons.add("第" + rowNum + "行：账号不能为空");
                    continue;
                }
                if (realName == null || realName.trim().isEmpty()) {
                    failed++;
                    failedReasons.add("第" + rowNum + "行：姓名不能为空");
                    continue;
                }
                Integer role = null;
                try {
                    role = Integer.valueOf(roleStr.trim());
                    if (role < 0 || role > 2) {
                        failed++;
                        failedReasons.add("第" + rowNum + "行：角色值无效（0=学生,1=教师,2=管理员）");
                        continue;
                    }
                } catch (Exception e) {
                    failed++;
                    failedReasons.add("第" + rowNum + "行：角色值无效（应为 0/1/2）");
                    continue;
                }

                SysUser user = new SysUser();
                user.setUsername(username.trim());
                user.setRealName(realName.trim());
                user.setRole(role);
                user.setPhone(phone);
                user.setEmail(email);

                // 班级名→id 映射
                if (className != null && !className.trim().isEmpty()) {
                    Long classId = classMap.get(className.trim());
                    if (classId == null) {
                        com.exam.exam_system.entity.SysClass cls = sysClassMapper.selectByName(className.trim());
                        if (cls != null) {
                            classId = cls.getId();
                            classMap.put(className.trim(), classId);
                        }
                    }
                    if (classId != null) {
                        user.setClassId(classId);
                    } else {
                        failedReasons.add("第" + rowNum + "行：班级「" + className.trim() + "」不存在，已跳过班级分配");
                    }
                }

                try {
                    createUser(user);
                    success++;
                } catch (IllegalArgumentException e) {
                    failed++;
                    failedReasons.add("第" + rowNum + "行：" + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel 解析失败：" + e.getMessage(), e);
        }

        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", success);
        result.put("failed", failed);
        result.put("failedReasons", failedReasons);
        return result;
    }

    /** 读取 Excel 单元格为字符串（学号避免科学计数法） */
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

    @Override
    public java.util.List<String> findExistingUsernames(java.util.List<String> usernames) {
        return userMapper.selectExistingUsernames(usernames);
    }

    @Override
    public java.util.Map<String, Object> getStudentProfile(Long userId) {
        // 学生版：复用通用版 getProfile，再补「studentNo = username / name = realName」两个别名
        // 保持老学生前端完全兼容（旧 studentProfile.js 字段名约定）
        java.util.Map<String, Object> profile = getProfile(userId);
        profile.putIfAbsent("studentNo", profile.get("username"));
        profile.putIfAbsent("name", profile.get("realName"));
        return profile;
    }

    @Override
    public java.util.Map<String, Object> getProfile(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("登录状态异常，请重新登录");
        }
        java.util.Map<String, Object> profile = userMapper.selectProfileById(userId);
        if (profile == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        // role 字段统一转 Integer，避免 MyBatis 返回 Byte/Short 导致前端类型不匹配
        Object roleVal = profile.get("role");
        if (roleVal != null) {
            profile.put("role", ((Number) roleVal).intValue());
        }
        // status 同理
        Object statusVal = profile.get("status");
        if (statusVal != null) {
            profile.put("status", ((Number) statusVal).intValue());
        }
        // 兼容：为老版学生前端补两个别名（新版管理/教师端前端也可直接用）
        profile.putIfAbsent("studentNo", profile.get("username"));
        profile.putIfAbsent("name", profile.get("realName"));
        return profile;
    }

    @Override
    public void updateContact(Long userId, String phone, String email) {
        if (userId == null) {
            throw new IllegalArgumentException("登录状态异常，请重新登录");
        }
        // 去空白；空字符串统一规整为 null，避免数据库出现 "" 这种"伪空值"
        String p = phone == null ? null : phone.trim();
        String e = email == null ? null : email.trim();
        if (p != null && p.isEmpty()) p = null;
        if (e != null && e.isEmpty()) e = null;

        // 手机号格式校验：11 位数字；可为空
        if (p != null && !p.matches("^1\\d{10}$")) {
            throw new IllegalArgumentException("手机号格式不正确，应为 11 位数字");
        }
        // 邮箱格式校验：简单正则，可为空
        if (e != null && !e.matches("^[\\w.\\-+]+@[\\w\\-]+(\\.[\\w\\-]+)+$")) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }

        int rows = userMapper.updateContact(userId, p, e);
        if (rows == 0) {
            throw new IllegalArgumentException("用户不存在，更新失败");
        }
    }

    @Override
    @Deprecated
    public void updateStudentContact(Long userId, String phone, String email) {
        // 学生老接口：直接委托通用版
        updateContact(userId, phone, email);
    }

    /**
     * 上传并保存当前账号头像（个人信息页「头像上传」）。
     *
     * <p>落盘：uploadDir/avatar/yyyyMM/uuid.ext（与课程封面同一套约定），
     * 返回 /uploads/avatar/... 供前端直接当 img src 使用，并把该路径写入 sys_user.avatar。
     */
    @Override
    public String updateAvatar(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new IllegalArgumentException("登录状态异常，请重新登录");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的头像图片");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        int dot = originalName.lastIndexOf('.');
        String ext = dot < 0 ? "" : originalName.substring(dot + 1).toLowerCase();
        if (!AVATAR_ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("头像仅支持 jpg/jpeg/png/gif/webp/bmp 格式的图片");
        }
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw new IllegalArgumentException("头像图片不能超过 2MB");
        }
        // uploadDir 统一解析成绝对路径（Tomcat 部署时 CWD 是临时目录，相对路径不可靠）
        String relativeDir = "avatar/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
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
            throw new IllegalStateException("头像保存失败：" + e.getMessage(), e);
        }

        String url = "/uploads/" + relativeDir + "/" + storedName;
        int rows = userMapper.updateAvatar(userId, url);
        if (rows == 0) {
            throw new IllegalArgumentException("用户不存在，头像更新失败");
        }
        return url;
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (userId == null) {
            throw new IllegalArgumentException("登录状态异常，请重新登录");
        }
        if (oldPassword == null || oldPassword.isEmpty()
                || newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("原密码与新密码均不能为空");
        }
        // 密码长度限制：6~16 位字符，支持字母数字
        if (!newPassword.matches("^[A-Za-z0-9]{6,16}$")) {
            throw new IllegalArgumentException("新密码长度需为 6-16 位，仅支持字母和数字");
        }

        // 取当前账号（含密码哈希），校验原密码
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        // 旧密码与库中哈希比对
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }
        // 新旧密码不能相同（用明文对哈希再比对一次，避免改了个"长得一样"的哈希）
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new IllegalArgumentException("新密码不能与原密码相同");
        }

        // 加密新密码后入库（禁止明文存储）
        String bcryptHash = passwordEncoder.encode(newPassword);
        int rows = userMapper.updatePassword(userId, bcryptHash);
        if (rows == 0) {
            throw new IllegalArgumentException("密码更新失败，请稍后重试");
        }
    }

    @Override
    @Deprecated
    public void changeStudentPassword(Long userId, String oldPassword, String newPassword) {
        // 学生老接口：直接委托通用版
        changePassword(userId, oldPassword, newPassword);
    }
}