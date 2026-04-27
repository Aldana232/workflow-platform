package com.workflow.workflowplatform.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeptStatsDTO {
    private long pendingCount;
    private long completedThisMonth;
    private double avgDurationHours;
    private long totalThisYear;
    private List<MonthlyBar> monthlyBars;
    private List<ActivityItem> recentActivity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyBar {
        private String month;
        private long completed;
        private int pct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityItem {
        private String code;
        private String processName;
        private String nodeName;
        private String completedAt;
        private String result;
    }
}
