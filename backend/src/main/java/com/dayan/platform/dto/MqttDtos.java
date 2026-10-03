package com.dayan.platform.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MqttDtos {

    private MqttDtos() {
    }

    public record ConnectionRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 128)
            @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]*$") String clientId,
            @NotBlank @Size(max = 500) String brokerUrl,
            @Size(max = 500) String username,
            @Size(max = 1000) String password,
            @NotNull Boolean tlsEnabled,
            @NotNull Boolean cleanSession,
            @NotNull @Min(5) @Max(3600) Integer keepAliveSeconds,
            @NotNull @Min(1) @Max(120) Integer connectionTimeoutSeconds,
            @NotNull Boolean enabled
    ) {
    }

    public record SubscriptionRequest(
            @NotNull @Min(1) Long connectionId,
            @NotBlank @Size(max = 500) String topicFilter,
            @NotNull @Min(0) @Max(2) Integer qos,
            @Size(max = 500) String description,
            @NotNull Boolean enabled
    ) {
    }

    public record StatusRequest(@NotNull Boolean enabled) {
    }
}
