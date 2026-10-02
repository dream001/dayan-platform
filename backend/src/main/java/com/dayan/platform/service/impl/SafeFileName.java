package com.dayan.platform.service.impl;

import java.text.Normalizer;
import org.springframework.util.StringUtils;

final class SafeFileName {

    private static final int MAXIMUM_LENGTH = 255;

    private SafeFileName() {
    }

    static String clean(String candidate) {
        String value = candidate == null ? "" : Normalizer.normalize(candidate, Normalizer.Form.NFKC);
        value = value.replace('\\', '/');
        int separator = value.lastIndexOf('/');
        if (separator >= 0) {
            value = value.substring(separator + 1);
        }
        value = value.replaceAll("[\\p{Cntrl}]", "")
                .replaceAll("[<>:\"|?*]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        if (!StringUtils.hasText(value) || ".".equals(value) || "..".equals(value)) {
            value = "file";
        }
        if (value.length() > MAXIMUM_LENGTH) {
            value = value.substring(0, MAXIMUM_LENGTH).trim();
        }
        return value;
    }
}
