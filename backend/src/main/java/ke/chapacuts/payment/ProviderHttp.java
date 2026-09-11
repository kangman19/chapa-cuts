package ke.chapacuts.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.util.function.Consumer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Thin wrapper so provider clients get status + parsed JSON back without exceptions on 4xx/5xx. */
@Component
public class ProviderHttp {

    public record Result(int status, JsonNode body) {

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public String text(String... path) {
            JsonNode node = body;
            for (String p : path) {
                node = node.path(p);
            }
            return node.isMissingNode() || node.isNull() ? null : node.asText();
        }
    }

    private final RestClient http;
    private final ObjectMapper mapper;

    public ProviderHttp(RestClient providerRestClient, ObjectMapper mapper) {
        this.http = providerRestClient;
        this.mapper = mapper;
    }

    public Result get(String url, Consumer<HttpHeaders> headers) {
        return http.get().uri(url).headers(headers).exchange(this::toResult);
    }

    public Result postJson(String url, Consumer<HttpHeaders> headers, Object body) {
        return http.post().uri(url).headers(headers).contentType(MediaType.APPLICATION_JSON).body(body)
                .exchange(this::toResult);
    }

    private Result toResult(org.springframework.http.HttpRequest req, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse res)
            throws java.io.IOException {
        String raw = res.bodyTo(String.class);
        JsonNode json = MissingNode.getInstance();
        if (raw != null && !raw.isBlank()) {
            try {
                json = mapper.readTree(raw);
            } catch (Exception e) {
                json = MissingNode.getInstance();
            }
        }
        return new Result(res.getStatusCode().value(), json);
    }
}
