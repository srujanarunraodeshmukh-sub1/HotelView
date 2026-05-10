package com.raghunath.hotelview.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "database_counters")
public class DatabaseCounter {
    @Id
    private String id; // This will be "hotel_id_sequence"
    private long seq;
}