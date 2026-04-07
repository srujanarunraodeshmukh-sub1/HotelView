package com.raghunath.hotelview.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Profile("dev") // Runs only when spring.profiles.active=dev
public class HotelTableAddManual implements CommandLineRunner {

    private final boolean RUN_TABLE_INIT = false;
    private final String URL = "https://hotelview.onrender.com/api/v1/tables/add";

    // PASTE YOUR FRESH TOKEN HERE
    private final String ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJob3RlbElkIjoiaG90ZWwwMDEiLCJzdWIiOiJob3RlbDAwMSIsImlhdCI6MTc3NDQ0NDQ1OSwiZXhwIjoxNzc0NDQ4MDU5fQ.SahmZcKraAVWpQM5nrIVWq3gnoNWy8a5WCTkhobuC4A";

    @Override
    public void run(String... args) throws Exception {
        if (!RUN_TABLE_INIT) return;

        ObjectMapper mapper = new ObjectMapper();
        RestTemplate restTemplate = new RestTemplate();

        // Load the Table JSON from resources
        InputStream is = getClass().getResourceAsStream("/table_test_data.json");
        if (is == null) {
            System.err.println("CRITICAL ERROR: Could not find HotelTableAddManual.json in resources!");
            return;
        }

        List<Map<String, Object>> tables = mapper.readValue(is, new TypeReference<>() {});

        // Setup Headers with Bearer Token
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(ACCESS_TOKEN);

        // Thread pool for parallel execution
        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicInteger successCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        System.out.println(">>> TABLE INITIALIZATION START: Sending " + tables.size() + " tables to database...");

        for (Map<String, Object> table : tables) {
            executor.submit(() -> {
                try {
                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(table, headers);
                    restTemplate.postForEntity(URL, requestEntity, String.class);

                    successCount.incrementAndGet();
                    System.out.println("Added Table: " + table.get("tableNumber"));
                } catch (Exception e) {
                    System.err.println("Failed to add Table " + table.get("tableNumber") + ": " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1, TimeUnit.MINUTES);

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\n--- TABLE INIT RESULTS ---");
        System.out.println("Total Time: " + duration + "ms");
        System.out.println("Successfully Created: " + successCount.get() + "/" + tables.size());

        if (successCount.get() == 0) {
            System.err.println("HINT: Check if 'hotel001' exists in your Admin collection or if the Token is valid.");
        }
    }
}