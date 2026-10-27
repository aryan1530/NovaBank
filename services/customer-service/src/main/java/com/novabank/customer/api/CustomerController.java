package com.novabank.customer.api;

import com.novabank.common.exception.NotFoundException;
import com.novabank.common.security.SecuritySupport;
import com.novabank.customer.domain.Customer;
import com.novabank.customer.repo.CustomerRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
public class CustomerController {
    private final CustomerRepository repo;

    public CustomerController(CustomerRepository repo) {
        this.repo = repo;
    }

    public record CustomerView(Long id, Long userId, String fullName, String email, String phone, String kycStatus, String addressLine, String city) {
        static CustomerView of(Customer c) {
            return new CustomerView(c.getId(), c.getUserId(), c.getFullName(), c.getEmail(), c.getPhone(), c.getKycStatus(), c.getAddressLine(), c.getCity());
        }
    }

    public record UpdateProfileRequest(String phone, String addressLine, String city) {}
    public record InternalCreateRequest(Long userId, @NotBlank String fullName, @NotBlank String email) {}

    @PostMapping("/internal/customers")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CustomerView createInternal(@RequestBody InternalCreateRequest req) {
        return repo.findByUserId(req.userId()).map(CustomerView::of).orElseGet(() -> {
            Customer c = new Customer();
            c.setUserId(req.userId());
            c.setFullName(req.fullName());
            c.setEmail(req.email());
            return CustomerView.of(repo.save(c));
        });
    }

    @GetMapping("/api/v1/customers/me")
    public CustomerView me() {
        return repo.findByUserId(SecuritySupport.currentUserId()).map(CustomerView::of)
                .orElseThrow(() -> new NotFoundException("Customer profile not found"));
    }

    @PutMapping("/api/v1/customers/me")
    @Transactional
    public CustomerView update(@RequestBody UpdateProfileRequest req) {
        Customer c = repo.findByUserId(SecuritySupport.currentUserId()).orElseThrow(() -> new NotFoundException("Customer profile not found"));
        if (req.phone() != null) c.setPhone(req.phone());
        if (req.addressLine() != null) c.setAddressLine(req.addressLine());
        if (req.city() != null) c.setCity(req.city());
        c.setUpdatedAt(Instant.now());
        return CustomerView.of(c);
    }

    @GetMapping("/api/v1/customers/{id}")
    public CustomerView get(@PathVariable Long id) {
        Customer c = repo.findById(id).orElseThrow(() -> new NotFoundException("Customer not found"));
        SecuritySupport.requireSelfOrAdmin(c.getUserId());
        return CustomerView.of(c);
    }

    @GetMapping("/internal/customers/by-user/{userId}")
    public CustomerView byUser(@PathVariable Long userId) {
        return repo.findByUserId(userId).map(CustomerView::of).orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    @PostMapping("/api/v1/customers/{id}/kyc")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CustomerView kyc(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Customer c = repo.findById(id).orElseThrow(() -> new NotFoundException("Customer not found"));
        c.setKycStatus(body.getOrDefault("status", "VERIFIED"));
        c.setUpdatedAt(Instant.now());
        return CustomerView.of(c);
    }

    @GetMapping("/api/v1/customers")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CustomerView> list() {
        return repo.findAll().stream().map(CustomerView::of).toList();
    }
}
