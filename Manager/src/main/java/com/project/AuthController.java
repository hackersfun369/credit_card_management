package com.project;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@CrossOrigin(origins = {
	    "http://localhost:4200",
	    "http://localhost:5173"
	})

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final ManagerRepository managers;
    private final PasswordEncoder passwords;
    private final SecretKey key;
    private final long expirationMinutes;

    public AuthController(ManagerRepository managers, PasswordEncoder passwords,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.managers = managers; this.passwords = passwords;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        if (username.isBlank() || request.password() == null || request.password().length() < 8)
            return ResponseEntity.badRequest().body(Map.of("message", "Username and an 8-character password are required."));
        if (request.firstName() == null || request.firstName().isBlank() || request.lastName() == null || request.lastName().isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "First name and last name are required."));
        if (request.phoneNumber() == null || !String.valueOf(request.phoneNumber()).matches("\\d{10}"))
            return ResponseEntity.badRequest().body(Map.of("message", "Phone number must contain exactly 10 digits."));
        if (managers.existsByUsername(username)) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "A manager with this username already exists."));
        if (managers.existsByPhoneNumber(request.phoneNumber())) return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "A manager with this phone number already exists."));
        Manager manager = new Manager(); manager.setUsername(username); manager.setPasswordHash(passwords.encode(request.password()));
        manager.setFirstName(request.firstName().trim()); manager.setLastName(request.lastName().trim()); manager.setPhoneNumber(request.phoneNumber()); manager.setAddress(request.address()); manager.setStatus(STATUS.ACTIVE); manager.setRole("ADMIN");
        managers.save(manager); return ResponseEntity.status(HttpStatus.CREATED).body(tokenResponse(manager));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String username = request.username() == null ? "" : request.username().trim();
        if (username.isBlank() || request.password() == null || request.password().isBlank()) return ResponseEntity.badRequest().body(Map.of("message", "Username and password are required."));
        return managers.findByUsername(username)
            .<ResponseEntity<?>>map(manager -> {
                if (manager.getStatus() != STATUS.ACTIVE) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "This manager account is inactive."));
                if (!passwords.matches(request.password(), manager.getPasswordHash())) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Incorrect password."));
                return ResponseEntity.ok(tokenResponse(manager));
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No manager with that username exists.")));
    }
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "A valid session token is required."));
        try {
            String username = Jwts.parser().verifyWith(key).build().parseSignedClaims(authorization.substring(7)).getPayload().getSubject();
            return managers.findByUsername(username).filter(m -> m.getStatus() == STATUS.ACTIVE)
                    .<ResponseEntity<?>>map(m -> ResponseEntity.ok(tokenResponse(m)))
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Manager account is unavailable.")));
        } catch (Exception exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Your session has expired. Please sign in again."));
        }
    }
    private Map<String, Object> tokenResponse(Manager manager) {
        Instant expires = Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder().subject(manager.getUsername()).claim("role", manager.getRole()).issuedAt(java.util.Date.from(Instant.now())).expiration(java.util.Date.from(expires)).signWith(key).compact();
        return Map.of("accessToken", token, "tokenType", "Bearer", "expiresAt", expires.toString(), "username", manager.getUsername(), "role", manager.getRole());
    }
    public record LoginRequest(String username, String password) {}
    public record RegisterRequest(String username, String password, String firstName, String lastName, Long phoneNumber, String address) {}
}
