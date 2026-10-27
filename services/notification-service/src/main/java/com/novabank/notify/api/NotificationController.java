package com.novabank.notify.api;

import com.novabank.common.security.SecuritySupport;
import com.novabank.notify.domain.Notification;
import com.novabank.notify.repo.NotificationRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationRepository repo;

    public NotificationController(NotificationRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Notification> mine() {
        return repo.findByUserIdOrderByCreatedAtDesc(SecuritySupport.currentUserId());
    }

    @PostMapping("/{id}/read")
    public void read(@PathVariable Long id) {
        repo.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(SecuritySupport.currentUserId()) || SecuritySupport.hasRole("ADMIN")) {
                n.setRead(true);
                repo.save(n);
            }
        });
    }
}
