package com.novabank.txn.api;

import com.novabank.common.security.SecuritySupport;
import com.novabank.txn.service.AssistantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
    private final AssistantService assistant;

    public AssistantController(AssistantService assistant) {
        this.assistant = assistant;
    }

    public record ChatRequest(@NotBlank @Size(max = 500) String message) {}

    @PostMapping("/chat")
    public AssistantService.Reply chat(@Valid @RequestBody ChatRequest request) {
        return assistant.reply(SecuritySupport.currentUserId(), request.message());
    }
}
