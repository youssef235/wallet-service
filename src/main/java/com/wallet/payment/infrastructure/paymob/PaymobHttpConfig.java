package com.wallet.payment.infrastructure.paymob;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The granular spring-boot-starter-webmvc starter does not autoconfigure a
 * RestClient.Builder, so provide one explicitly for the Paymob gateway, with
 * hard timeouts so a hung provider can never hold a request thread forever.
 */
@Configuration
class PaymobHttpConfig {

    @Bean
    RestClient.Builder restClientBuilder(
            @Value("${app.paymob.connect-timeout:3s}") Duration connectTimeout,
            @Value("${app.paymob.read-timeout:10s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
