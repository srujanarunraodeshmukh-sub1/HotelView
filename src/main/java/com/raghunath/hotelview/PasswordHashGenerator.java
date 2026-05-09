package com.raghunath.hotelview;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {

    public static void main(String[] args) {

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String rawPassword = "08091973";

        String hashedPassword = encoder.encode(rawPassword);

        System.out.println("Original Password: " + rawPassword);
        System.out.println("Hashed Password: " + hashedPassword);
    }
}