package com.dayan.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank
            @Size(max = 64)
            String username,
            @NotBlank
            @Size(max = 72)
            String password
    ) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 512) String refreshToken) {
    }

    public record LogoutRequest(@NotBlank @Size(max = 512) String refreshToken) {
    }

    public record ProfileUpdateRequest(
            @NotBlank @Size(max = 100) String displayName,
            @Email @Size(max = 254) String email,
            @Pattern(regexp = "^$|^[0-9+() -]{3,32}$", message = "phone format is invalid")
            String phone
    ) {
    }

    public record PasswordChangeRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Size(min = 12, max = 72) String newPassword
    ) {
    }
}
