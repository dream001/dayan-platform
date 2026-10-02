package com.dayan.platform.vo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void calculatesTotalPagesAndCopiesItems() {
        List<String> source = new ArrayList<>(List.of("a", "b"));

        PageResponse<String> response = PageResponse.of(2, 2, 5, source);
        source.clear();

        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.items()).containsExactly("a", "b");
        assertThatThrownBy(() -> response.items().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsInvalidMetadata() {
        assertThatThrownBy(() -> PageResponse.of(0, 20, 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
