package com.raghunath.hotelview.dto.admin;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsDTO {
    // Green Dot Values
    private Long activeTablesCount;         // 12 ACTIVE
    private Long pendingHomeDeliveriesCount;  // 5 PENDING
    private Long completedOrdersTodayCount;  // 128 TODAY
    private Long employeeOnlineCount;        // 14 ONLINE
    private Long totalItemsCount;            // 45 TOTAL
    private Double todaySalesRupees;         // 15300.0 (till now)
}