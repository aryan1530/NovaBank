package com.novabank.identity.api.dto;

import java.util.List;

public record UserView(Long id, String username, String email, List<String> roles, boolean enabled) {}
