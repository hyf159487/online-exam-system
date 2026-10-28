package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体，对应 sys_role 表
 *
 * <p>固定 3 种角色（系统不允许多增，但表结构支持扩展）：
 * <ul>
 *   <li>id=1  student  学生</li>
 *   <li>id=2  teacher  教师</li>
 *   <li>id=3  admin    管理员（超级管理员，禁止删除）</li>
 * </ul>
 *
 * <p>与 sys_user.role (0/1/2) 形成 1:1 映射：role_id = role + 1
 */
@Data
public class SysRole {

    private Long id;

    /** 角色名称：学生/教师/管理员 */
    private String roleName;

    /** 角色标识：student/teacher/admin */
    private String roleKey;

    /** 角色备注 */
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
