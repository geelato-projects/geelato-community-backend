package cn.geelato.web.platform.boot.event;

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
    private static final String ES_URL = "http://es.geelato.cn/";
    private static final String ES_USERNAME = "elastic";
    private static final String ES_PASSWORD = "geelatoEs";

    @Bean
    public ElasticsearchClient esClient() {
        if (ES_URL == null || ES_URL.isEmpty()) {
            return null;
        }
        URI uri = URI.create(ES_URL);
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();
        int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(scheme) ? 443 : 80) : uri.getPort();
        HttpHost host = new HttpHost(uri.getHost(), port, scheme);
        org.elasticsearch.client.RestClientBuilder builder = RestClient.builder(host);
        if (ES_USERNAME != null && !ES_USERNAME.isEmpty()) {
            String token = Base64.getEncoder().encodeToString((ES_USERNAME + ":" + ES_PASSWORD).getBytes());
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
