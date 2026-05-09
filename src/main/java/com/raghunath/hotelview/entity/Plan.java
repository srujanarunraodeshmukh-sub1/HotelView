package com.raghunath.hotelview.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "plans") // This must match the collection name in MongoDB
public class Plan {
    @Id
    private String id;
    private String planKey;
    private String name;
    private String description;
    private double price;
    private List<String> features;
    private boolean includesPrinter;
    private boolean isCustom;
}