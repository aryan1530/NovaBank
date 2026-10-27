package com.novabank.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class CustomerProvisioner {
    private static final Logger log = LoggerFactory.getLogger(CustomerProvisioner.class);
    private final RestClient restClient;
    private final String customerBase;

    public CustomerProvisioner(@Value("${novabank.customer-service-url:http://localhost:8082}") String customerBase) {
        this.customerBase = customerBase;
        this.restClient = RestClient.create();
    }

    public void provision(Long userId, String fullName, String email) {
        try {
            restClient.post()
                    .uri(customerBase + "/internal/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("userId", userId, "fullName", fullName, "email", email))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Customer provision deferred for user {}: {}", userId, ex.getMessage());
        }
    }
}
