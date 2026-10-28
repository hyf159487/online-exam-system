package com.exam.exam_system.mapper;

import com.exam.exam_system.entity.PaperVisibleUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 试卷可见教师中间表 Mapper
 *
 * <p>标准多对多设计：插入/删除/查询都走 paper_id + user_id 组合。
 */
@Mapper
public interface PaperVisibleUserMapper {

    /** 全量替换某试卷的共享名单（先删后插，事务由 Service 控制） */
    @Delete("DELETE FROM exam_paper_visible_user WHERE paper_id = #{paperId}")
    int deleteByPaperId(@Param("paperId") Long paperId);

    /** 单条插入 */
    @Insert("INSERT INTO exam_paper_visible_user(paper_id, user_id) VALUES(#{paperId}, #{userId})")
    int insert(@Param("paperId") Long paperId, @Param("userId") Long userId);

    /** 查某试卷被共享给了哪些教师 ID */
    @Select("SELECT user_id FROM exam_paper_visible_user WHERE paper_id = #{paperId} ORDER BY user_id")
    List<Long> listUserIdsByPaperId(@Param("paperId") Long paperId);

    /** 批量查询：多张试卷分别被共享给了谁（用于列表页一次性回填 visibleNames） */
    @Select("<script>" +
            "SELECT paper_id, user_id FROM exam_paper_visible_user WHERE paper_id IN " +
            "<foreach collection='paperIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            " ORDER BY paper_id, user_id" +
            "</script>")
    List<PaperVisibleUser> listByPaperIds(@Param("paperIds") List<Long> paperIds);
}
