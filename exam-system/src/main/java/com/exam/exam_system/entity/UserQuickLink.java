package com.exam.exam_system.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户自定义快捷入口实体，对应 user_quick_link 表。
 *
 * <p>首页「快捷入口」区允许用户从自己的菜单权限里挑选入口，也可以自己填名称 + 路由，
 * 配置保存在数据库里、跟账号走：换浏览器 / 换电脑登录后依然是自己挑的那几个入口。
 * 当前仅管理员端首页开放「自定义」按钮，教师端仍用系统默认入口。
 *
 * <p>存储结构为「一行一个入口」：user_id + label + route_path + icon + sort_no，
 * 读取时按 sort_no 升序即为首页上的展示顺序。
 */
@Data
public class UserQuickLink {

    /** 主键 */
    private Long id;

    /** 用户 ID（sys_user.id） */
    private Long userId;

    /** 入口名称，如「待审核试题」 */
    private String label;

    /** 路由地址，如 /audit/pending */
    private String routePath;

    /** 图标名（Element Plus 图标组件名，如 Checked / Files）；为空时前端用默认图标 */
    private String icon;

    /** 展示顺序：从 0 开始，越小越靠前 */
    private Integer sortNo;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 最后更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
