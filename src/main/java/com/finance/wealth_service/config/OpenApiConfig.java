package com.finance.wealth_service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI wealthServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Wealth Service API")
                .version("v1")
                .description("API para acompanhar contas, buckets, objetivos e transações financeiras.")
                .contact(new Contact().name("Wealth Service")));
    }
}
