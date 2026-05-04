package com.wirs.inventory.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.domain.model.SkuAllocationOrder;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class SkuAllocationOrderTest {

    @Test
    void of_unsortedList_returnsSortedBySkuAscending() {
        var order = SkuAllocationOrder.of(List.of(
            new ReservationItem("C300", 1),
            new ReservationItem("A100", 2),
            new ReservationItem("B200", 3)
        ));
        assertThat(order.items()).extracting("sku")
            .containsExactly("A100", "B200", "C300");
    }

    @Test
    void of_alreadySortedList_returnsUnchangedOrder() {
        var order = SkuAllocationOrder.of(List.of(
            new ReservationItem("A100", 1),
            new ReservationItem("B200", 1)
        ));
        assertThat(order.items()).extracting("sku")
            .containsExactly("A100", "B200");
    }

    @Test
    void of_singleItem_returnsSingleItem() {
        var order = SkuAllocationOrder.of(List.of(new ReservationItem("Z999", 5)));
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).sku()).isEqualTo("Z999");
    }
}
