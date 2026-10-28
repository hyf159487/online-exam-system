package com.exam.exam_system.service;

import com.exam.exam_system.common.PageResult;
import com.exam.exam_system.dto.UserQuery;
import com.exam.exam_system.entity.SysUser;

import java.util.List;

public interface UserService {

    /** 账号密码登录 */
    SysUser login(String username, String password);

    /** 按角色查用户（不分页） */
    List<SysUser> listByRole(Integer role);

    /** 按角色查用户总数（仪表盘统计用），role=null 查全部 */
    long countByRole(Integer role);

    /** 分页 + 模糊搜索查用户（学生管理页用） */
    PageResult<SysUser> page(UserQuery query);

    /** 更新账号状态（0=禁用 1=启用） */
    void updateStatus(Long id, Integer status);

    /** 重置密码（password 已 BCrypt 哈希） */
    void updatePassword(Long id, String bcryptPassword);

    /** 调整账号所属班级（classId 可传 null 取消归属） */
    void updateClassId(Long id, Long classId);

    /**
     * 批量调班（管理员，单人/批量复用）
     * @param studentIds 学生 id 数组（1 个或多个）
     * @param targetClassId 目标班级 id
     * @return 实际迁移的学生数（已在目标班级的自动跳过）
     */
    int updateClassBatch(List<Long> studentIds, Long targetClassId);

    /**
     * 批量移出本班（class_id 置 null，账号保留）
     * @param studentIds 学生 id 数组
     * @return 实际移出的学生数
     */
    int removeClassBatch(List<Long> studentIds);

    /** 按班级查学生列表（班级管理 - 查看学生） */
    List<SysUser> listByClassId(Long classId);

    /** 新增账号（管理员用）。密码为空则默认 123456，自动 BCrypt 哈希；账号重复抛异常 */
    SysUser createUser(SysUser user);

    /**
     * 批量新增账号（管理员用）：逐条复用单人创建逻辑（密码默认/BCrypt），账号重复的自动跳过
     * @return {success: 成功条数, skipped: 跳过条数(重复或字段为空)}
     */
    java.util.Map<String, Integer> createBatch(java.util.List<SysUser> users);

    /**
     * Excel 批量导入账号（管理员用）
     * @param file Excel 文件（.xlsx），列：账号/学号、姓名、角色(0学生/1教师/2管理员)、班级名(可选)、手机号(可选)、邮箱(可选)
     * @return {success: 成功条数, failed: 失败条数, failedReasons: 失败原因列表}
     */
    java.util.Map<String, Object> importExcel(org.springframework.web.multipart.MultipartFile file);

    /**
     * 批量删除账号（管理员用）：admin 账号在 SQL 层强制保护；管理员账号至少保留一个，
     * 若本次删除会导致系统没有任何管理员，则管理员账号自动跳过（部分失败策略）
     * @return {success: 实际删除条数, skipped: 跳过条数, skippedReasons: 跳过原因列表}
     */
    java.util.Map<String, Object> deleteBatch(java.util.List<Long> ids);

    /** 批量查询已存在的账号名（批量新增弹窗实时提醒用） */
    java.util.List<String> findExistingUsernames(java.util.List<String> usernames);

    /**
     * 查询当前登录学生的个人信息（学号/姓名/班级/年级/院系只读，手机号/邮箱可改）
     * <p>返回字段：studentNo / name / role / className / grade / department / phone / email
     * @param userId 当前登录用户 id（由 JWT 拦截器注入）
     */
    java.util.Map<String, Object> getStudentProfile(Long userId);

    /**
     * 查询任意角色用户的「通用版」个人信息（管理员 / 教师 / 学生 都能走）。
     * <p>与学生版相比，额外输出：username / realName / roleName(中文名) / status；
     * 保持兼容：同时写出 studentNo=username、name=realName，旧版学生前端直接无缝工作。
     * @param userId 当前登录用户 id（由 JWT 拦截器注入）
     */
    java.util.Map<String, Object> getProfile(Long userId);

    /**
     * 仅更新当前账号的手机号与邮箱（任意角色通用，SQL 只动这两列）
     * @param userId 当前登录用户 id
     * @param phone  手机号（允许为空）
     * @param email  邮箱（允许为空）
     */
    void updateContact(Long userId, String phone, String email);

    /**
     * 上传并更新当前账号头像（任意角色通用，只改 sys_user.avatar 一列）。
     * <p>图片落盘到 uploadDir/avatar/yyyyMM/uuid.ext，返回可访问路径（/uploads/avatar/...），
     * 前端直接把它当头像 src 使用。
     * @param userId 当前登录用户 id（由 JWT 拦截器注入）
     * @param file   头像图片（jpg/jpeg/png/gif/webp/bmp，不超过 2MB）
     * @return 头像的可访问路径
     */
    String updateAvatar(Long userId, org.springframework.web.multipart.MultipartFile file);

    /**
     * 仅更新当前学生账号的手机号与邮箱。
     * 学号/姓名/班级/年级/院系等身份字段在 SQL 层即被丢弃，不接受更新。
     * @param userId 当前登录用户 id
     * @param phone  手机号（允许为空）
     * @param email  邮箱（允许为空）
     * @deprecated 推荐使用 {@link #updateContact(Long, String, String)}（多角色通用）
     */
    @Deprecated
    void updateStudentContact(Long userId, String phone, String email);

    /**
     * 用户自助修改密码（任意角色通用：管理员/教师/学生）。
     * 校验原密码 → 校验新旧不同 → BCrypt 加密后入库。
     * 只能改当前登录账号的密码，不能操作他人。
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 学生自助修改密码：校验原密码 → 校验新旧不同 → BCrypt 加密后入库。
     * 只能改当前登录账号的密码，不能操作他人。
     * @param userId      当前登录用户 id（由 JWT 拦截器注入）
     * @param oldPassword 旧密码明文
     * @param newPassword 新密码明文
     * @deprecated 推荐使用 {@link #changePassword(Long, String, String)}（多角色通用）
     */
    @Deprecated
    void changeStudentPassword(Long userId, String oldPassword, String newPassword);
}