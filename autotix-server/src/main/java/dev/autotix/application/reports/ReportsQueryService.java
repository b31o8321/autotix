package dev.autotix.application.reports;

import dev.autotix.infrastructure.persistence.channel.ChannelEntity;
import dev.autotix.infrastructure.persistence.channel.mapper.ChannelMapper;
import dev.autotix.infrastructure.persistence.ticket.mapper.TicketMetricsMapper;
import dev.autotix.infrastructure.persistence.user.UserEntity;
import dev.autotix.infrastructure.persistence.user.mapper.UserMapper;
import dev.autotix.interfaces.desk.dto.ReportsSummaryDTO;
import dev.autotix.interfaces.desk.dto.ReportsSummaryDTO.AgentSolvedCountDTO;
import dev.autotix.interfaces.desk.dto.ReportsSummaryDTO.ChannelOpenCountDTO;
import dev.autotix.interfaces.desk.dto.ReportsSummaryDTO.DateCountDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates ticket metrics for the Reports dashboard.
 * All time math uses UTC to keep tests deterministic.
 */
@Service
public class ReportsQueryService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final TicketMetricsMapper metricsMapper;
    private final ChannelMapper channelMapper;
    private final UserMapper userMapper;

    public ReportsQueryService(TicketMetricsMapper metricsMapper,
                               ChannelMapper channelMapper,
                               UserMapper userMapper) {
        this.metricsMapper = metricsMapper;
        this.channelMapper = channelMapper;
        this.userMapper = userMapper;
    }

    public ReportsSummaryDTO buildSummary() {
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        String cutoff30d = nowUtc.minusDays(30).format(TS_FMT);
        String cutoff24h = nowUtc.minusHours(24).format(TS_FMT);

        ReportsSummaryDTO dto = new ReportsSummaryDTO();

        // KPI
        dto.openTickets = metricsMapper.countOpen();
        dto.solvedToday = metricsMapper.countSolvedSince(cutoff24h);

        List<Long> frtSeconds = metricsMapper.firstResponseSecondsSince(cutoff30d);
        dto.medianFirstResponseSeconds = median(frtSeconds);

        int total = metricsMapper.slaTotalSince(cutoff30d);
        int breached = total > 0 ? metricsMapper.slaBreachedSince(cutoff30d) : 0;
        dto.slaBreachRatePct = total > 0 ? (breached * 100.0 / total) : 0.0;

        // Series (last 14 days)
        LocalDate today = nowUtc.toLocalDate();
        LocalDate from14 = today.minusDays(13);  // 14 days inclusive
        String seriesFrom = from14.atStartOfDay().format(TS_FMT);
        String seriesTo = today.plusDays(1).atStartOfDay().format(TS_FMT);

        dto.createdSeries = buildSeries(
                metricsMapper.createdPerDay(seriesFrom, seriesTo), from14, today);
        dto.solvedSeries = buildSeries(
                metricsMapper.solvedPerDay(seriesFrom, seriesTo), from14, today);

        // By channel - enrich with channel metadata in Java
        List<Map<String, Object>> channelRows = metricsMapper.openByChannel();
        dto.byChannel = enrichChannels(channelRows);

        // By agent - enrich with user metadata in Java
        List<Map<String, Object>> agentRows = metricsMapper.solvedByAgent(cutoff30d, 10);
        dto.byAgent = enrichAgents(agentRows);

        return dto;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<ChannelOpenCountDTO> enrichChannels(List<Map<String, Object>> rows) {
        List<ChannelOpenCountDTO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String channelId = str(row.get("CHANNELID"));
            long openCount = toLong(row.get("OPENCOUNT"));

            ChannelOpenCountDTO c = new ChannelOpenCountDTO();
            c.channelId = channelId;
            c.openCount = openCount;
            c.displayName = channelId; // fallback
            c.platform = "CUSTOM";     // fallback

            // Try to load channel info by numeric ID
            if (channelId != null) {
                try {
                    Long numId = Long.parseLong(channelId);
                    ChannelEntity ch = channelMapper.selectById(numId);
                    if (ch != null) {
                        c.displayName = ch.getDisplayName() != null ? ch.getDisplayName() : channelId;
                        c.platform = ch.getPlatform() != null ? ch.getPlatform() : "CUSTOM";
                    }
                } catch (NumberFormatException ignored) {
                    // non-numeric channel_id (test data) — use fallbacks
                }
            }
            result.add(c);
        }
        return result;
    }

    private List<AgentSolvedCountDTO> enrichAgents(List<Map<String, Object>> rows) {
        List<AgentSolvedCountDTO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String agentId = str(row.get("AGENTID"));
            long solvedCount = toLong(row.get("SOLVEDCOUNT"));

            AgentSolvedCountDTO a = new AgentSolvedCountDTO();
            a.agentId = agentId;
            a.solvedCount = solvedCount;
            a.displayName = agentId; // fallback

            // Try to load user display name
            if (agentId != null) {
                try {
                    Long numId = Long.parseLong(agentId);
                    UserEntity u = userMapper.selectById(numId);
                    if (u != null && u.getDisplayName() != null) {
                        a.displayName = u.getDisplayName();
                    }
                } catch (NumberFormatException ignored) {
                    // non-numeric assignee_id (test/legacy data) — use fallback
                }
            }
            result.add(a);
        }
        return result;
    }

    private List<DateCountDTO> buildSeries(List<Map<String, Object>> rows,
                                            LocalDate from, LocalDate to) {
        Map<String, Long> byDate = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String key = str(row.get("DATE_KEY"));
            if (key != null) {
                if (key.length() > 10) {
                    key = key.substring(0, 10);
                }
                byDate.put(key, toLong(row.get("CNT")));
            }
        }

        List<DateCountDTO> series = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            String ds = d.format(DATE_FMT);
            series.add(new DateCountDTO(ds, byDate.getOrDefault(ds, 0L)));
        }
        return series;
    }

    private Long median(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        } else {
            return (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
        }
    }

    private String str(Object v) {
        return v == null ? null : v.toString();
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
