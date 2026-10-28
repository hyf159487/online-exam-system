package com.exam.exam_system.dto;

import lombok.Data;

/**
 * 用户查询参数（账号管理页用）
 *
 * <p>筛选条件：角色、班级精确匹配；学号精确匹配（整串相等）；姓名模糊匹配（LIKE %关键字%）。
 * 通过 {@link #getOffset()} 计算分页偏移量。
 */
@Data
public class UserQuery {

    /** 第几页（从 1 开始），默认 1 */
    private Integer pageNum = 1;

    /** 每页多少条，默认 10 */
    private Integer pageSize = 10;

    /** 按角色筛（可选）：0=学生 1=教师 2=管理员 */
    private Integer role;

    /** 按学号精确查询（可选，整串相等；只输部分学号查不到） */
    private String username;

    /** 按姓名模糊查询（可选，匹配包含该关键字的姓名） */
    private String realName;

    /** 按班级筛选（可选，仅学生 role=0 时才有 meaning） */
    private Long classId;

    /** 学号：去掉首尾空格后参与等值匹配，避免复制粘贴带空格时查不到 */
    public String getUsername() {
        return username == null ? null : username.trim();
    }

    /** 姓名：去掉首尾空格后参与模糊匹配 */
    public String getRealName() {
        return realName == null ? null : realName.trim();
    }

    /** MyBatis 动态 SQL 用：分页偏移 = (页码-1) * 每页条数 */
    public int getOffset() {
        int pn = pageNum == null ? 1 : pageNum;
        int ps = pageSize == null ? 10 : pageSize;
        return Math.max(0, (pn - 1) * ps);
    }
}
