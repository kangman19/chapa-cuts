package ke.chapacuts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpConfig {

    /** One client for talking to payment providers. Daraja can be slow in sandbox, so the read timeout is generous. */
    @Bean
    public RestClient providerRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(40_000);
        return RestClient.builder().requestFactory(factory).build();
    }
}
