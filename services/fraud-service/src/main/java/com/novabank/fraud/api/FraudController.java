package com.novabank.fraud.api;

import com.novabank.common.exception.NotFoundException;
import com.novabank.common.exception.BusinessException;
import com.novabank.common.security.SecuritySupport;
import com.novabank.fraud.domain.FraudAlert;
import com.novabank.fraud.repo.FraudAlertRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
public class FraudController {
    private final FraudAlertRepository repo;
    private final RestClient rest = RestClient.create();
    private final String mlUrl;

    public FraudController(FraudAlertRepository repo, @Value("${novabank.ml-url}") String mlUrl) {
        this.repo = repo;
        this.mlUrl = mlUrl;
    }

    @PostMapping("/internal/score")
    public Map<String, Object> score(@RequestBody Map<String, Object> features) {
        try {
            Map<?, ?> ml = rest.post().uri(mlUrl + "/predict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(features)
                    .retrieve()
                    .body(Map.class);
            BigDecimal score = new BigDecimal(String.valueOf(ml.get("score")));
            String reasons = String.valueOf(ml.containsKey("reasons") ? ml.get("reasons") : "model");
            if (score.compareTo(new BigDecimal("0.5")) >= 0) {
                FraudAlert a = new FraudAlert();
                a.setUserId(features.get("userId") == null ? null : Long.valueOf(features.get("userId").toString()));
                a.setScore(score);
                a.setReasons(reasons);
                a.setStatus(score.compareTo(new BigDecimal("0.85")) >= 0 ? "OPEN" : "WATCH");
                repo.save(a);
            }
            return Map.of("score", score, "reasons", reasons);
        } catch (Exception ex) {
            return Map.of("score", new BigDecimal("0.20"), "reasons", "ml_unavailable_heuristic");
        }
    }

    @GetMapping("/api/v1/fraud/alerts")
    @PreAuthorize("hasAnyRole('ADMIN','FRAUD_ANALYST')")
    public List<FraudAlert> alerts(@RequestParam(defaultValue = "OPEN") String status) {
        return repo.findByStatusOrderByCreatedAtDesc(status);
    }

    @GetMapping("/api/v1/fraud/model-metrics")
    @PreAuthorize("hasAnyRole('ADMIN','FRAUD_ANALYST')")
    public Map<?, ?> modelMetrics() {
        return modelData("/metrics", "FRAUD_METRICS_UNAVAILABLE");
    }

    @GetMapping("/api/v1/fraud/demo-overview")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<?, ?> demoOverview() {
        return modelData("/demo/overview", "DEMO_DATA_UNAVAILABLE");
    }

    private Map<?, ?> modelData(String path, String code) {
        try {
            Map<?, ?> response = rest.get().uri(mlUrl + path).retrieve().body(Map.class);
            if (response == null) throw new IllegalStateException("Empty ML response");
            return response;
        } catch (Exception ex) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, code,
                    "Fraud insights are temporarily unavailable. Please try again shortly.");
        }
    }

    @PostMapping("/api/v1/fraud/alerts/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN','FRAUD_ANALYST')")
    public FraudAlert review(@PathVariable Long id, @RequestBody Map<String, String> body) {
        FraudAlert a = repo.findById(id).orElseThrow(() -> new NotFoundException("Alert not found"));
        a.setStatus(body.getOrDefault("status", "CLEARED"));
        a.setReviewNotes(body.getOrDefault("notes", ""));
        a.setReviewerId(SecuritySupport.currentUserId());
        return repo.save(a);
    }
}
