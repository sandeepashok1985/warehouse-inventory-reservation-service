package com.wirs.inventory.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wirs.inventory.reservation.domain.model.OrderedIdList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class OrderedIdListTest {

    @Test
    void of_sortsIdsAlphabetically() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID third = UUID.fromString("00000000-0000-0000-0000-000000000003");

        OrderedIdList list = OrderedIdList.of(List.of(third, first, second));

        assertThat(list.ids()).containsExactly(first, second, third);
    }

    @Test
    void of_handlesEmptyList() {
        OrderedIdList list = OrderedIdList.of(List.of());
        assertThat(list.ids()).isEmpty();
    }

    @Test
    void of_handlesSingleElement() {
        UUID id = UUID.randomUUID();
        OrderedIdList list = OrderedIdList.of(List.of(id));
        assertThat(list.ids()).containsExactly(id);
    }
}
