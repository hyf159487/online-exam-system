package com.exam.exam_system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库表 sys_user
 * 字段命名：数据库下划线 ↔ Java 驼峰（由 mybatis map-underscore-to-camel-case 自动映射）
 */
@Data
public class SysUser {

    private Long id;
    private String username;
    private String password;
    private String realName;

    /** 角色（兼容字段）：0 学生 / 1 教师 / 2 管理员
     *  与 sys_role.id 形成 role+1 映射：0→1, 1→2, 2→3 */
    private Integer role;

    /** RBAC 新增：角色ID（关联 sys_role.id，1学生/2教师/3管理员） */
    private Long roleId;

    private Long classId;
    private String phone;

    /** 邮箱（学生端个人信息维护） */
    private String email;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 错题本自动移除开关：0=关 1=开（自主练习/错题本重做用） */
    private Integer wrongAutoRemove;

    /** 做对几次后移出错题本：1 或 3（配合 wrong_auto_remove 使用） */
    private Integer wrongRemoveThreshold;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
