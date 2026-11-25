package com.wallet.payment.infrastructure.paymob;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.paymob")
public record PaymobProperties(boolean enabled,
                               String baseUrl,
                               String apiKey,
                               long integrationId,
                               long iframeId,
                               String hmacSecret) {
}
