package com.example.investmentdatascannerservice.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TokenValidationTest {

    @Value("${tinkoff.api.token}")
    private String token;

    @Test
    void testTokenIsLoaded() {
        assertNotNull(token, "Token should not be null");
        assertFalse(token.isEmpty(), "Token should not be empty");
        assertTrue(token.startsWith("t."), "Token should start with 't.'");
        System.out.println("Token loaded successfully: " + token.substring(0, 10) + "...");
    }
}
