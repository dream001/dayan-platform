package com.dayan.platform.model;

public record ApplicationMetadata(
        String name,
        String environment,
        String version
) {
}
