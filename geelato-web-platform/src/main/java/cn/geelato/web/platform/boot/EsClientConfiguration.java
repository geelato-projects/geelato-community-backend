package cn.geelato.web.platform.boot;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.JsonpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.util.Base64;

@Configuration
public class EsClientConfiguration {
    @Value("${geelato.es.url:}")
    private String esUrl;

    @Value("${geelato.es.username:}")
    private String esUsername;

    @Value("${geelato.es.password:}")
    private String esPassword;

    @Bean
    public JsonpMapper esJsonpMapper() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new JacksonJsonpMapper(om);
    }

    @Bean
    public ElasticsearchClient esClient(JsonpMapper esJsonpMapper) {
        String url = esUrl == null ? null : esUrl.trim();
        if (url == null || url.isEmpty()) {
            return null;
        }
        URI uri = URI.create(url);
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(scheme) ? 443 : 80) : uri.getPort();
        HttpHost host = new HttpHost(uri.getHost(), port, scheme);
        org.elasticsearch.client.RestClientBuilder builder = RestClient.builder(host);
        String username = esUsername == null ? null : esUsername.trim();
        String password = esPassword == null ? null : esPassword.trim();
        if (username != null && !username.isEmpty()) {
            String token = Base64.getEncoder().encodeToString((username + ":" + (password == null ? "" : password)).getBytes());
            Header[] headers = new Header[]{new BasicHeader("Authorization", "Basic " + token)};
            builder.setDefaultHeaders(headers);
        }
        RestClient restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, esJsonpMapper);
        return new ElasticsearchClient(transport);
    }
}
