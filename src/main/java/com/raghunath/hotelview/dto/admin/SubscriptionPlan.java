package com.raghunath.hotelview.dto.admin;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@Document(collection = "plans")
public class SubscriptionPlan {
    @Id
    private String id;
    private String planName;
    private double price;
    private List<String> features;
    private boolean includesPrinter;
    private boolean isCustom;
}