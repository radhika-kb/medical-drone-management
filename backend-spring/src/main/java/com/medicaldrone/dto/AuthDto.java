package com.medicaldrone.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AuthDto {

    public static class LoginRequest {
        @NotBlank @Email
        private String email;
        @NotBlank
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RegisterRequest {
        @NotBlank
        private String name;
        @NotBlank @Email
        private String email;
        @NotBlank
        @Size(min = 8, max = 72)
        private String password;
        @NotBlank
        private String role;
        @NotNull
        private Long hospitalId;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public Long getHospitalId() { return hospitalId; }
        public void setHospitalId(Long hospitalId) { this.hospitalId = hospitalId; }
    }

    public static class RefreshRequest {
        @NotBlank
        private String refreshToken;

        public String getRefreshToken() { return refreshToken; }
        public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    }

    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private String tokenType = "Bearer";
        private Long userId;
        private String name;
        private String email;
        private String role;

        private Long hospitalId;
        private String hospitalName;

        public AuthResponse(String accessToken, String refreshToken, Long userId, String name, String email, String role) {
            this(accessToken, refreshToken, userId, name, email, role, null, null);
        }

        public AuthResponse(String accessToken, String refreshToken, Long userId, String name, String email, String role, Long hospitalId, String hospitalName) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.role = role;
            this.hospitalId = hospitalId;
            this.hospitalName = hospitalName;
        }

        public String getAccessToken() { return accessToken; }
        public String getRefreshToken() { return refreshToken; }
        public String getTokenType() { return tokenType; }
        public Long getUserId() { return userId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getRole() { return role; }
        public Long getHospitalId() { return hospitalId; }
        public String getHospitalName() { return hospitalName; }
    }
}
