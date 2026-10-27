package com.novabank.identity.bootstrap;

import com.novabank.identity.domain.Role;
import com.novabank.identity.domain.User;
import com.novabank.identity.repo.RoleRepository;
import com.novabank.identity.repo.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements CommandLineRunner {
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;

    public AdminSeeder(UserRepository users, RoleRepository roles, PasswordEncoder encoder) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        seed("admin", "admin@novabank.local", "AdminPassw0rd!", "ADMIN");
        seed("analyst", "analyst@novabank.local", "AnalystPassw0rd!", "FRAUD_ANALYST");
    }

    private void seed(String username, String email, String password, String roleName) {
        if (users.existsByUsername(username)) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(password));
        Role role = roles.findByName(roleName).orElseThrow();
        user.getRoles().add(role);
        users.save(user);
    }
}
