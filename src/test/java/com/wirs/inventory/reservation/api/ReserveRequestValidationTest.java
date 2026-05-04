package com.wirs.inventory.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.wirs.inventory.reservation.api.dto.request.ReserveItemRequest;
import com.wirs.inventory.reservation.api.dto.request.ReserveRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class ReserveRequestValidationTest {

    private final Validator validator =
        Validation.buildDefaultValidatorFactory().getValidator();

    private ReserveRequest valid() {
        return new ReserveRequest("ORD-1", List.of(new ReserveItemRequest("A100", 5L)));
    }

    @Test
    void validRequest_noViolations() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void nullOrderId_producesViolation() {
        var req = new ReserveRequest(null, List.of(new ReserveItemRequest("A100", 5L)));
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void blankOrderId_producesViolation() {
        var req = new ReserveRequest("  ", List.of(new ReserveItemRequest("A100", 5L)));
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void emptyItems_producesViolation() {
        var req = new ReserveRequest("ORD-1", List.of());
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void itemWithZeroQuantity_producesViolation() {
        var req = new ReserveRequest("ORD-1", List.of(new ReserveItemRequest("A100", 0L)));
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void itemWithNegativeQuantity_producesViolation() {
        var req = new ReserveRequest("ORD-1", List.of(new ReserveItemRequest("A100", -1L)));
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void itemWithBlankSku_producesViolation() {
        var req = new ReserveRequest("ORD-1", List.of(new ReserveItemRequest("  ", 5L)));
        assertThat(validator.validate(req)).isNotEmpty();
    }
}
