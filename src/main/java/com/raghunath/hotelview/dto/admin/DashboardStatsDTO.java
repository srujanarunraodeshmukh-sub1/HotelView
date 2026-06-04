package com.raghunath.hotelview.dto.admin;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsDTO {
    // Green Dot Values
    private String restaurantName;
    private String planType;
    private Long activeTablesCount;         // 12 ACTIVE
    private Long HomeDeliveriesCount;  // 5 PENDING
    private Long completedOrdersTodayCount;  // 128 TODAY
    private Long employeeOnlineCount;        // 14 ONLINE
    private Long totalItemsCount;            // 45 TOTAL
    private Double todaySalesRupees;         // 15300.0 (till now)
    private Double last24HoursSalesRupees;
    private Long last24HoursOrdersCount;
}