package ke.chapacuts.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mpesa")
public record MpesaProperties(
        String baseUrl,
        String consumerKey,
        String consumerSecret,
        String passkey,
        String shortcode,
        String callbackUrl) {

    public boolean isConfigured() {
        return notBlank(consumerKey) && notBlank(consumerSecret) && notBlank(passkey)
                && notBlank(shortcode) && notBlank(callbackUrl);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
