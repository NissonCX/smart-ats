package com.smartats.module.analytics.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 分析统计 Mapper
 * <p>
 * 专注于聚合查询，不继承 BaseMapper，避免暴露不必要的 CRUD 方法。
 * 使用 {@code <script>} 动态 SQL 实现可选条件过滤。
 */
@Mapper
public interface AnalyticsMapper {

    /**
     * 按申请状态分组统计数量
     *
     * @param jobId     职位 ID（可选）
     * @param startDate 起始时间（可选）
     * @param endDate   截止时间（可选）
     * @return List of {status: String, cnt: Long}
     */
    @Select({"<script>",
            "SELECT status, COUNT(*) AS cnt FROM job_applications",
            "<where>",
            "  <if test='jobId != null'>AND job_id = #{jobId}</if>",
            "  <if test='startDate != null'>AND applied_at &gt;= #{startDate}</if>",
            "  <if test='endDate != null'>AND applied_at &lt;= #{endDate}</if>",
            "</where>",
            "GROUP BY status",
            "</script>"})
    List<Map<String, Object>> countGroupByStatus(@Param("jobId") Long jobId,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);

    /**
     * 按月统计申请趋势
     *
     * @return List of {month: "yyyy-MM", cnt: Long}
     */
    @Select({"<script>",
            "SELECT DATE_FORMAT(applied_at, '%Y-%m') AS month, COUNT(*) AS cnt",
            "FROM job_applications",
            "<where>",
            "  <if test='jobId != null'>AND job_id = #{jobId}</if>",
            "  <if test='startDate != null'>AND applied_at &gt;= #{startDate}</if>",
            "  <if test='endDate != null'>AND applied_at &lt;= #{endDate}</if>",
            "</where>",
            "GROUP BY month ORDER BY month",
            "</script>"})
    List<Map<String, Object>> countByMonth(@Param("jobId") Long jobId,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * 计算平均 AI 匹配分数
     */
    @Select({"<script>",
            "SELECT AVG(match_score) FROM job_applications",
            "WHERE match_score IS NOT NULL",
            "<if test='jobId != null'>AND job_id = #{jobId}</if>",
            "<if test='startDate != null'>AND applied_at &gt;= #{startDate}</if>",
            "<if test='endDate != null'>AND applied_at &lt;= #{endDate}</if>",
            "</script>"})
    BigDecimal avgMatchScore(@Param("jobId") Long jobId,
                             @Param("startDate") LocalDateTime startDate,
                             @Param("endDate") LocalDateTime endDate);

    /**
     * 计算平均招聘周期（天）：从申请到发出 Offer 的平均天数
     */
    @Select({"<script>",
            "SELECT AVG(DATEDIFF(updated_at, applied_at)) FROM job_applications",
            "WHERE status = 'OFFER'",
            "<if test='jobId != null'>AND job_id = #{jobId}</if>",
            "<if test='startDate != null'>AND applied_at &gt;= #{startDate}</if>",
            "<if test='endDate != null'>AND applied_at &lt;= #{endDate}</if>",
            "</script>"})
    Double avgDaysToOffer(@Param("jobId") Long jobId,
                          @Param("startDate") LocalDateTime startDate,
                          @Param("endDate") LocalDateTime endDate);

    /**
     * 统计已安排的面试总数
     */
    @Select({"<script>",
            "SELECT COUNT(*) FROM interview_records",
            "WHERE status IN ('SCHEDULED', 'COMPLETED')",
            "<if test='startDate != null'>AND scheduled_at &gt;= #{startDate}</if>",
            "<if test='endDate != null'>AND scheduled_at &lt;= #{endDate}</if>",
            "</script>"})
    long countInterviews(@Param("startDate") LocalDateTime startDate,
                         @Param("endDate") LocalDateTime endDate);
}
