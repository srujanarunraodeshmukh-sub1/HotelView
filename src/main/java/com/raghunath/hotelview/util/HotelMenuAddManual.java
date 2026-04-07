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
@Profile("dev")
public class HotelMenuAddManual implements CommandLineRunner {

    private final boolean RUN_STRESS_TEST = false;
    private final String URL = "https://hotelview-production.up.railway.app/api/v1/menu/add";

    // PASTE YOUR TOKEN HERE
    private final String ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiQURNSU4iLCJob3RlbElkIjoiaG90ZWwwMDEiLCJzdWIiOiJob3RlbDAwMSIsImlhdCI6MTc3NTU1MDg4OSwiZXhwIjoxNzc1NTUxNzg5fQ.5cONVgTNyQ7I7mJ6S8_yDy7E1OPhCxlcIwjqVUP5oAU";

    @Override
    public void run(String... args) throws Exception {
        if (!RUN_STRESS_TEST) return;

        ObjectMapper mapper = new ObjectMapper();
        RestTemplate restTemplate = new RestTemplate();

        // Load the 56 items
        InputStream is = getClass().getResourceAsStream("/menu_test_data.json");
        if (is == null) {
            System.err.println("CRITICAL ERROR: Could not find menu_test_data.json in resources!");
            return;
        }

        List<Map<String, Object>> items = mapper.readValue(is, new TypeReference<>() {});

        // Setup Headers for Authorization
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(ACCESS_TOKEN);

        // 10 concurrent users hitting the API
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger successCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        System.out.println(">>> STRESS TEST START: Sending 56 authorized requests via 10 parallel threads...");

        for (Map<String, Object> item : items) {
            executor.submit(() -> {
                try {
                    // Wrap item and headers into an entity
                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(item, headers);

                    restTemplate.postForEntity(URL, requestEntity, String.class);
                    successCount.incrementAndGet();
                    System.out.println("Success: " + item.get("name"));
                } catch (Exception e) {
                    System.err.println("Request failed for " + item.get("name") + ": " + e.getMessage());
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.MINUTES);

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\n--- FINAL STRESS TEST RESULTS ---");
        System.out.println("Total Time: " + duration + "ms");
        System.out.println("Successful: " + successCount.get() + "/119");
        System.out.println("Average Latency per request: " + (duration / 119.0) + "ms");

        if (successCount.get() == 0) {
            System.err.println("HINT: If you got 403, check if your Token is expired or CSRF is disabled on Render.");
        }
    }
}