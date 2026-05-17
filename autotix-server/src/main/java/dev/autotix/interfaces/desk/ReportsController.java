package dev.autotix.interfaces.desk;

import dev.autotix.application.reports.ReportsQueryService;
import dev.autotix.interfaces.desk.dto.ReportsSummaryDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports dashboard endpoint — agent-level read access (JWT required, same as DeskController).
 *
 * GET /api/desk/reports/summary
 */
@RestController
@RequestMapping("/api/desk/reports")
public class ReportsController {

    private final ReportsQueryService reportsQueryService;

    public ReportsController(ReportsQueryService reportsQueryService) {
        this.reportsQueryService = reportsQueryService;
    }

    @GetMapping("/summary")
    public ReportsSummaryDTO summary() {
        return reportsQueryService.buildSummary();
    }
}
