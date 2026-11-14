package com.unilodge.web.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata and the bearer-token authorisation scheme. */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI unilodgeOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("UniLodge API")
                        .version("1.0.0")
                        .description("Student accommodation for Ghana. Browse approved listings, "
                                + "manage landlord listings and run administrator moderation. "
                                + "Direct contact only — UniLodge never processes rent payments.")
                        .contact(new Contact().name("UniLodge").email("support@unilodge.local")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
