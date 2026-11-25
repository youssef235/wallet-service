package com.wallet.payment.infrastructure.paymob;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The granular spring-boot-starter-webmvc starter does not autoconfigure a
 * RestClient.Builder, so provide one explicitly for the Paymob gateway.
 */
@Configuration
class PaymobHttpConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
