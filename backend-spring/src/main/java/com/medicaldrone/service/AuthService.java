package com.medicaldrone.service;

import com.medicaldrone.dto.AuthDto;
import com.medicaldrone.model.RefreshToken;
import com.medicaldrone.model.Role;
import com.medicaldrone.model.User;
import com.medicaldrone.model.MedicalCenter;
import com.medicaldrone.repository.MedicalCenterRepository;
import com.medicaldrone.repository.RefreshTokenRepository;
import com.medicaldrone.repository.RoleRepository;
import com.medicaldrone.repository.UserRepository;
import com.medicaldrone.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final MedicalCenterRepository medicalCenterRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository, MedicalCenterRepository medicalCenterRepository, RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository, PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.medicalCenterRepository = medicalCenterRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String role = user.getRole() != null ? user.getRole().getName() : "ROLE_MEDICAL_CENTER_USER";
        String accessToken = tokenProvider.generateAccessToken(user.getEmail(), role);
        String refreshTokenStr = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken(
                user.getId(),
                refreshTokenStr,
                LocalDateTime.now().plusDays(7)
        );
        refreshTokenRepository.save(refreshToken);

        Long hospitalId = user.getHospital() != null ? user.getHospital().getId() : null;
        String hospitalName = user.getHospital() != null ? user.getHospital().getName() : null;

        return new AuthDto.AuthResponse(accessToken, refreshTokenStr, user.getId(), user.getName(), user.getEmail(), role, hospitalId, hospitalName);
    }

    @Transactional
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already in use");
        }

        String requestedRole = request.getRole().trim().toUpperCase();
        if (!Role.REQUESTING_HOSPITAL.equals(requestedRole) && !Role.SUPPLYING_HOSPITAL.equals(requestedRole)) {
            throw new IllegalArgumentException("Choose a requesting or supplying medical center account");
        }
        MedicalCenter hospital = medicalCenterRepository.findById(request.getHospitalId())
                .filter(center -> "APPROVED".equalsIgnoreCase(center.getVerificationStatus()))
                .filter(center -> "ACTIVE".equalsIgnoreCase(center.getStatus()) && "ACTIVE".equalsIgnoreCase(center.getOperationalStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Select an approved, active Bengaluru hospital or blood bank"));
        Role role = roleRepository.findByName(requestedRole)
                .orElseThrow(() -> new IllegalArgumentException("Account role is not configured"));

        User user = new User(
                request.getName().trim(),
                email,
                passwordEncoder.encode(request.getPassword()),
                role,
                hospital
        );
        userRepository.save(user);

        String accessToken = tokenProvider.generateAccessToken(user.getEmail(), role.getName());
        String refreshTokenStr = UUID.randomUUID().toString();

        RefreshToken refreshToken = new RefreshToken(
                user.getId(),
                refreshTokenStr,
                LocalDateTime.now().plusDays(7)
        );
        refreshTokenRepository.save(refreshToken);

        return new AuthDto.AuthResponse(accessToken, refreshTokenStr, user.getId(), user.getName(), user.getEmail(), role.getName(), hospital.getId(), hospital.getName());
    }

    @Transactional
    public AuthDto.AuthResponse refresh(AuthDto.RefreshRequest request) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (token.isExpired() || token.isRevoked()) {
            throw new IllegalArgumentException("Expired or revoked refresh token");
        }

        // Revoke current refresh token (Rotation)
        token.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(token);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String role = user.getRole() != null ? user.getRole().getName() : "ROLE_MEDICAL_CENTER_USER";
        String newAccessToken = tokenProvider.generateAccessToken(user.getEmail(), role);
        String newRefreshTokenStr = UUID.randomUUID().toString();

        RefreshToken newRefreshToken = new RefreshToken(
                user.getId(),
                newRefreshTokenStr,
                LocalDateTime.now().plusDays(7)
        );
        refreshTokenRepository.save(newRefreshToken);

        return new AuthDto.AuthResponse(newAccessToken, newRefreshTokenStr, user.getId(), user.getName(), user.getEmail(), role);
    }
}
