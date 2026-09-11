package ke.chapacuts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paystack")
public record PaystackProperties(
        String baseUrl,
        String secretKey,
        String currency,
        String returnUrl) {

    public boolean isConfigured() {
        return secretKey != null && !secretKey.isBlank();
    }
}
