package com.wirs.inventory.reservation.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.wirs.inventory.reservation.api.dto.response.PagedResponse;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@Tag("unit")
class PagedResponseTest {

    @Test
    void from_createsPagedResponseFromSpringPage() {
        List<String> content = List.of("a", "b");
        Page<String> springPage = new PageImpl<>(content, PageRequest.of(0, 10), 25);

        PagedResponse<String> response = PagedResponse.from(springPage);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(25);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void from_handlesEmptyPage() {
        Page<String> emptyPage = Page.empty(PageRequest.of(0, 20));

        PagedResponse<String> response = PagedResponse.from(emptyPage);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }
}
