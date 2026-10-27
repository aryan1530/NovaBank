package com.novabank.identity.service;

import com.novabank.common.exception.BusinessException;
import com.novabank.common.security.JwtProperties;
import com.novabank.common.security.JwtService;
import com.novabank.identity.api.dto.*;
import com.novabank.identity.domain.AuditLog;
import com.novabank.identity.domain.RefreshToken;
import com.novabank.identity.domain.Role;
import com.novabank.identity.domain.User;
import com.novabank.identity.repo.AuditLogRepository;
import com.novabank.identity.repo.RefreshTokenRepository;
import com.novabank.identity.repo.RoleRepository;
import com.novabank.identity.repo.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final AuditLogRepository audits;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JwtProperties jwtProperties;
    private final CustomerProvisioner customerProvisioner;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RoleRepository roles, RefreshTokenRepository refreshTokens,
                       AuditLogRepository audits, PasswordEncoder encoder, JwtService jwt,
                       JwtProperties jwtProperties, CustomerProvisioner customerProvisioner) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.audits = audits;
        this.encoder = encoder;
        this.jwt = jwt;
        this.jwtProperties = jwtProperties;
        this.customerProvisioner = customerProvisioner;
    }

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        if (users.existsByUsername(req.username()) || users.existsByEmail(req.email())) {
            throw new BusinessException(HttpStatus.CONFLICT, "USER_EXISTS", "Username or email already registered");
        }
        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(encoder.encode(req.password()));
        Role customer = roles.findByName("CUSTOMER").orElseThrow();
        user.getRoles().add(customer);
        users.save(user);
        customerProvisioner.provision(user.getId(), req.fullName(), req.email());
        audits.save(AuditLog.of(user.getId(), "REGISTER", "Customer registered"));
        return issue(user, UUID.randomUUID());
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = users.findByUsername(req.username())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid credentials"));
        if (!user.isEnabled() || !encoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Invalid credentials");
        }
        audits.save(AuditLog.of(user.getId(), "LOGIN", "Login success"));
        return issue(user, UUID.randomUUID());
    }

    @Transactional
    public TokenResponse refresh(String rawRefresh) {
        String hash = sha256(rawRefresh);
        RefreshToken existing = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Invalid refresh token"));
        if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            refreshTokens.revokeFamily(existing.getFamilyId());
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "REFRESH_REUSE", "Refresh token reuse detected");
        }
        existing.setRevoked(true);
        TokenResponse next = issue(existing.getUser(), existing.getFamilyId());
        return next;
    }

    @Transactional
    public void logout(String rawRefresh) {
        refreshTokens.findByTokenHash(sha256(rawRefresh)).ifPresent(t -> refreshTokens.revokeFamily(t.getFamilyId()));
    }

    public UserView me(Long userId) {
        User user = users.findById(userId).orElseThrow();
        return toView(user);
    }

    private TokenResponse issue(User user, UUID family) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();
        String access = jwt.createAccessToken(user.getId(), user.getUsername(), roleNames);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(sha256(rawRefresh));
        token.setFamilyId(family);
        token.setExpiresAt(Instant.now().plus(jwtProperties.getRefreshTokenDays(), ChronoUnit.DAYS));
        refreshTokens.save(token);
        return new TokenResponse(access, rawRefresh, "Bearer", jwtProperties.getAccessTokenMinutes() * 60, roleNames, user.getId(), user.getUsername());
    }

    private UserView toView(User user) {
        return new UserView(user.getId(), user.getUsername(), user.getEmail(),
                user.getRoles().stream().map(Role::getName).toList(), user.isEnabled());
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
