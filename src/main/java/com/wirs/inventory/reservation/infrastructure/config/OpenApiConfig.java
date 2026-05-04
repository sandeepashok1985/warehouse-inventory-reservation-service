package com.wirs.inventory.reservation.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures the OpenAPI 3 specification with API key security scheme. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryReservationApi() {
        return new OpenAPI()
            .info(new Info()
                .title("Warehouse Inventory Reservation API")
                .version("1.0.0"))
            .components(new Components()
                .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-API-Key")));
    }
}
