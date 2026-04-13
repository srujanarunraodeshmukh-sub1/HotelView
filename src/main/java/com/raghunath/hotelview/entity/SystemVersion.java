package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "system_versions")
public class SystemVersion {
    @Id
    private String id;

    @Indexed(unique = true)
    private String hotelId;

    private Long salesVersion;      // Timestamp in millis
    private Long tableVersion;      // Timestamp in millis
    private Long menuVersion;       // Timestamp in millis
    private Long employeeVersion;   // Timestamp in millis
    private Long kitchenVersion;    // Timestamp in millis

    private LocalDateTime lastUpdated;
}