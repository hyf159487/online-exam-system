package com.exam.exam_system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 学情报表预览 VO
 *
 * <p>三张报表（学生成绩 / 班级学情统计 / 试题质量）共用同一份结构，
 * 由 {@code StatsExportServiceImpl} 的数据构建方法产出，
 * <b>导出 Excel 与页面预览用的是同一份数据</b>，保证「预览即所得」。</p>
 *
 * <p>row 用 List（按列顺序放值）而不是 Map：列少且固定，
 * 顺序结构能避免拼错 key，前端按 index 取值即可。</p>
 */
@Data
public class ReportPreviewVO {

    /** 报表标题，如「学生成绩报表 - 期中考试」 */
    private String title;

    /** 数据行数（不含标题与表头） */
    private Integer total = 0;

    /** 列定义，顺序即展示顺序 */
    private List<Column> columns = new ArrayList<>();

    /** 数据行：每行的元素与 columns 一一对应（值为数字或已格式化文本） */
    private List<List<Object>> rows = new ArrayList<>();

    /** 追加一列 */
    public ReportPreviewVO addColumn(String prop, String label, Integer width) {
        this.columns.add(new Column(prop, label, width));
        return this;
    }

    /** 追加一行，同时维护行数 */
    public ReportPreviewVO addRow(List<Object> row) {
        this.rows.add(row);
        this.total = this.rows.size();
        return this;
    }

    /**
     * 列定义
     */
    @Data
    public static class Column {

        /** 列标识（前端 key / Excel 无实义，仅作唯一标识） */
        private String prop;

        /** 列名（表头文字） */
        private String label;

        /** 建议列宽（px，供前端表格与 Excel 列宽参考） */
        private Integer width;

        public Column() {
        }

        public Column(String prop, String label, Integer width) {
            this.prop = prop;
            this.label = label;
            this.width = width;
        }
    }
}
