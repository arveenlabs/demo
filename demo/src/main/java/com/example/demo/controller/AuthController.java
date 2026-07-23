package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepo, JwtUtil jwtUtil, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    private String nowIso() {
        return Instant.now().toString();
    }

    private TokenResponse issueTokens(String userId) {
        String aJti = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String rJti = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String access = jwtUtil.createAccessToken(userId, aJti);
        String refresh = jwtUtil.createRefreshToken(userId, rJti);
        userRepo.findById(userId).ifPresent(u -> {
            u.setRefreshJti(rJti);
            userRepo.save(u);
        });
        return new TokenResponse(access, refresh);
    }

    private Map<String, Object> userPublic(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("email", u.getEmail());
        m.put("name", u.getName());
        m.put("avatar", u.getAvatar());
        m.put("phone", u.getPhone());
        m.put("is_admin", u.isAdmin());
        return m;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.OK)
    public TokenResponse signup(@Valid @RequestBody SignupRequest body) {
        if (userRepo.findByEmail(body.getEmail().toLowerCase()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setEmail(body.getEmail().toLowerCase());
        user.setName(body.getName());
        user.setPasswordHash(passwordEncoder.encode(body.getPassword()));
        user.setDisabled(false);
        user.setAdmin(false);
        user.setCreatedAt(nowIso());
        userRepo.save(user);
        return issueTokens(user.getId());
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest body) {
        User user = userRepo.findByEmail(body.getEmail().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password"));
        if (!passwordEncoder.matches(body.getPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
        }
        return issueTokens(user.getId());
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest body) {
        Claims claims;
        try {
            claims = jwtUtil.parseClaims(body.getRefreshToken());
            if (!"refresh".equals(claims.get("type", String.class))) {
                throw new JwtException("bad type");
            }
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        String userId = claims.getSubject();
        String jti = claims.getId();
        User user = userRepo.findById(userId)
                .filter(u -> jti.equals(u.getRefreshJti()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token revoked"));
        return issueTokens(user.getId());
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@Valid @RequestBody ForgotPasswordRequest body) {
        String otp = String.format("%06d", new Random().nextInt(1_000_000));
        userRepo.findByEmail(body.getEmail().toLowerCase()).ifPresent(u -> {
            u.setOtp(otp);
            u.setOtpExp(Instant.now().plus(10, ChronoUnit.MINUTES).toString());
            userRepo.save(u);
        });
        return Map.of("message", "OTP sent (demo)", "demo_otp", otp);
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest body) {
        User user = userRepo.findByEmail(body.getEmail().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP"));
        if (!body.getOtp().equals(user.getOtp())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }
        if (user.getOtpExp() == null || Instant.parse(user.getOtpExp()).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired");
        }
        user.setPasswordHash(passwordEncoder.encode(body.getNewPassword()));
        user.setOtp(null);
        user.setOtpExp(null);
        user.setRefreshJti(null);
        userRepo.save(user);
        return Map.of("message", "Password reset successful");
    }

    @PostMapping("/logout")
    public Map<String, String> logout(Authentication auth) {
        userRepo.findById(auth.getName()).ifPresent(u -> {
            u.setRefreshJti(null);
            userRepo.save(u);
        });
        return Map.of("message", "Logged out");
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        User user = userRepo.findById(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return userPublic(user);
    }

    @PatchMapping("/me")
    public Map<String, Object> updateMe(@RequestBody UpdateProfileRequest body, Authentication auth) {
        User user = userRepo.findById(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        if (body.getName() != null) user.setName(body.getName());
        if (body.getPhone() != null) user.setPhone(body.getPhone());
        if (body.getAvatar() != null) user.setAvatar(body.getAvatar());
        userRepo.save(user);
        return userPublic(user);
    }
}
