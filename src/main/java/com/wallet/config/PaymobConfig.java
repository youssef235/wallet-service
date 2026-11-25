package com.wallet.config;

import com.wallet.payment.infrastructure.paymob.PaymobProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PaymobProperties.class)
public class PaymobConfig {
}
