package dev.autotix.infrastructure.persistence.ticket.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Read-only metrics queries for the Reports page.
 * Uses DATE() which is supported by H2 (MySQL compat mode), MySQL, and SQLite.
 */
@Mapper
public interface TicketMetricsMapper {

    // KPI: open tickets

    @Select("SELECT COUNT(*) FROM ticket WHERE status IN ('NEW','OPEN','WAITING_ON_CUSTOMER','WAITING_ON_INTERNAL')")
    int countOpen();

    // KPI: solved today

    @Select("SELECT COUNT(*) FROM ticket WHERE status = 'SOLVED' AND solved_at >= #{cutoff}")
    int countSolvedSince(@Param("cutoff") String cutoff);

    // KPI: median first-response (raw seconds list)

    @Select("SELECT TIMESTAMPDIFF(SECOND, created_at, first_response_at) "
            + "FROM ticket "
            + "WHERE first_response_at IS NOT NULL AND created_at >= #{cutoff}")
    List<Long> firstResponseSecondsSince(@Param("cutoff") String cutoff);

    // KPI: SLA breach rate

    @Select("SELECT COUNT(*) FROM ticket WHERE created_at >= #{cutoff}")
    int slaTotalSince(@Param("cutoff") String cutoff);

    @Select("SELECT COUNT(*) FROM ticket WHERE sla_breached = TRUE AND created_at >= #{cutoff}")
    int slaBreachedSince(@Param("cutoff") String cutoff);

    // Series: created per day

    @Select("SELECT DATE(created_at) AS date_key, COUNT(*) AS cnt "
            + "FROM ticket "
            + "WHERE created_at >= #{from} AND created_at < #{to} "
            + "GROUP BY DATE(created_at) "
            + "ORDER BY DATE(created_at)")
    List<Map<String, Object>> createdPerDay(@Param("from") String from, @Param("to") String to);

    // Series: solved per day

    @Select("SELECT DATE(solved_at) AS date_key, COUNT(*) AS cnt "
            + "FROM ticket "
            + "WHERE status = 'SOLVED' AND solved_at >= #{from} AND solved_at < #{to} "
            + "GROUP BY DATE(solved_at) "
            + "ORDER BY DATE(solved_at)")
    List<Map<String, Object>> solvedPerDay(@Param("from") String from, @Param("to") String to);

    // By channel - no JOIN to avoid CAST issues with non-numeric channel_id test data.
    // Channel display info is enriched in Java by ReportsQueryService.

    @Select("SELECT t.channel_id AS channelId, COUNT(*) AS openCount "
            + "FROM ticket t "
            + "WHERE t.status IN ('NEW','OPEN','WAITING_ON_CUSTOMER','WAITING_ON_INTERNAL') "
            + "GROUP BY t.channel_id")
    List<Map<String, Object>> openByChannel();

    // By agent - no JOIN; display info enriched in Java.

    @Select("SELECT t.assignee_id AS agentId, COUNT(*) AS solvedCount "
            + "FROM ticket t "
            + "WHERE t.status = 'SOLVED' AND t.assignee_id IS NOT NULL AND t.solved_at >= #{cutoff} "
            + "GROUP BY t.assignee_id "
            + "ORDER BY COUNT(*) DESC "
            + "LIMIT #{limit}")
    List<Map<String, Object>> solvedByAgent(@Param("cutoff") String cutoff, @Param("limit") int limit);
}
