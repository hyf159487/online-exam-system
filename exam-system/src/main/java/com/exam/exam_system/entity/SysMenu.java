package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 菜单实体，对应 sys_menu 表
 *
 * <p>树形结构（自关联 parent_id）：
 * <ul>
 *   <li>顶级菜单 parent_id=0</li>
 *   <li>子菜单 parent_id=父菜单ID</li>
 * </ul>
 *
 * <p>layout 字段区分布局：
 * <ul>
 *   <li>admin=在 Layout.vue 渲染（管理员/教师端）</li>
 *   <li>student=在 StudentLayout.vue 渲染（学生端）</li>
 * </ul>
 *
 * <p>children 由后端构建树时填充，DB 表里没有 children 列。
 */
@Data
public class SysMenu {

    private Long id;

    /** 父菜单ID，0=顶级菜单 */
    private Long parentId;

    /** 菜单名称 */
    private String menuName;

    /** 前端路由地址（父菜单可空，叶子菜单必填） */
    private String routePath;

    /** Element Plus 图标名（如 HomeFilled / Document） */
    private String icon;

    /** 同级排序号（升序） */
    private Integer sortOrder;

    /** 是否显示：1=是 0=否 */
    private Integer visible;

    /** 所属布局：admin=教师端Layout / student=学生端StudentLayout */
    private String layout;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 子菜单（构建树时填充，DB 无此列） */
    private List<SysMenu> children = new ArrayList<>();

    /** 是否有子菜单（构建树时填充，方便前端判断渲染为 el-sub-menu 还是 el-menu-item） */
    private Boolean hasChildren;
}
