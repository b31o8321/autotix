package dev.autotix.interfaces.desk.dto;

import java.util.List;

/**
 * Full payload returned by GET /api/desk/reports/summary.
 */
public class ReportsSummaryDTO {

    public int openTickets;
    public int solvedToday;
    /** Null when no first-response data exists for the window. */
    public Long medianFirstResponseSeconds;
    /** 0..100 percentage; 0.0 when no tickets in window. */
    public double slaBreachRatePct;

    /** Last 14 days, daily buckets, zero-filled. */
    public List<DateCountDTO> createdSeries;
    /** Last 14 days, daily buckets, zero-filled. */
    public List<DateCountDTO> solvedSeries;

    public List<ChannelOpenCountDTO> byChannel;
    public List<AgentSolvedCountDTO> byAgent;

    // ── Nested types ──────────────────────────────────────────────────────────

    public static class DateCountDTO {
        public String date;  // yyyy-MM-dd
        public long count;

        public DateCountDTO() {}
        public DateCountDTO(String date, long count) {
            this.date = date;
            this.count = count;
        }
    }

    public static class ChannelOpenCountDTO {
        public String channelId;
        public String displayName;
        public String platform;
        public long openCount;
    }

    public static class AgentSolvedCountDTO {
        public String agentId;
        public String displayName;
        public long solvedCount;
    }
}
