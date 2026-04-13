package cn.geelato.web.platform.boot.event;

import cn.geelato.web.platform.boot.properties.EsConfigurationProperties;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
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

import java.net.URI;
import java.util.Base64;

@Configuration
public class EsClientConfiguration {
    @Bean
    public ElasticsearchClient esClient(EsConfigurationProperties p) {
        if (p.getUrl() == null || p.getUrl().isEmpty()) {
            return null;
        }
        URI uri = URI.create(p.getUrl());
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(scheme) ? 443 : 80) : uri.getPort();
        HttpHost host = new HttpHost(uri.getHost(), port, scheme);
        org.elasticsearch.client.RestClientBuilder builder = RestClient.builder(host);
        if (p.getUsername() != null && !p.getUsername().isEmpty()) {
            String token = Base64.getEncoder().encodeToString((p.getUsername() + ":" + p.getPassword()).getBytes());
            Header[] headers = new Header[]{new BasicHeader("Authorization", "Basic " + token)};
            builder.setDefaultHeaders(headers);
        }
        RestClient restClient = builder.build();
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper(om));
        return new ElasticsearchClient(transport);
    }
}
