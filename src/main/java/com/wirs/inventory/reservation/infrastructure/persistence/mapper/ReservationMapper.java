package com.wirs.inventory.reservation.infrastructure.persistence.mapper;

import com.wirs.inventory.reservation.domain.model.Reservation;
import com.wirs.inventory.reservation.domain.model.ReservationItem;
import com.wirs.inventory.reservation.domain.state.ReservationState;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationEntity;
import com.wirs.inventory.reservation.infrastructure.persistence.entity.ReservationItemEntity;
import java.util.List;
import java.util.UUID;

/**
 * Utility mapper between {@link ReservationEntity} / {@link ReservationItemEntity} (JPA)
 * and {@link Reservation} / {@link ReservationItem} (domain).
 */
public final class ReservationMapper {


    /**
     * Converts a JPA entity to a domain {@link Reservation} aggregate.
     *
     * @param entity the JPA entity (never {@code null})
     * @return a new domain Reservation with its items
     */
    public static Reservation toDomain(ReservationEntity entity) {
        List<ReservationItem> items = entity.getItems().stream()
            .map(ReservationMapper::toDomainItem)
            .toList();
        return Reservation.builder()
            .id(entity.getId())
            .orderId(entity.getOrderId())
            .state(ReservationState.fromString(entity.getStatus()))
            .items(items)
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .expiresAt(entity.getExpiresAt())
            .build();
    }

    private static ReservationItem toDomainItem(ReservationItemEntity entity) {
        return ReservationItem.builder()
            .sku(entity.getSku())
            .quantity(entity.getQuantity())
            .build();
    }

    /**
     * Converts a domain {@link Reservation} to a JPA entity.
     * Items are also converted and linked to the entity.
     *
     * @param domain the domain aggregate
     * @return a new JPA entity (unsaved)
     */
    public static ReservationEntity toEntity(Reservation domain) {
        ReservationEntity entity = new ReservationEntity();
        entity.setId(domain.getId());
        entity.setOrderId(domain.getOrderId());
        entity.setStatus(domain.status());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        entity.setExpiresAt(domain.getExpiresAt());

        List<ReservationItemEntity> itemEntities = domain.getItems().stream()
            .map(item -> toEntityItem(entity, item))
            .toList();
        entity.setItems(itemEntities);
        return entity;
    }

    private static ReservationItemEntity toEntityItem(ReservationEntity parent, ReservationItem domain) {
        ReservationItemEntity entity = new ReservationItemEntity();
        entity.setId(UUID.randomUUID());
        entity.setReservation(parent);
        entity.setSku(domain.sku());
        entity.setQuantity(domain.quantity());
        return entity;
    }
}
