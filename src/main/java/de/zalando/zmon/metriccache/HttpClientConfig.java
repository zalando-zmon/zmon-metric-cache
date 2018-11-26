package de.zalando.zmon.metriccache;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.fluent.Executor;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties(prefix = "zmon.metricsconfig")
public class HttpClientConfig {

    private final int connectTimeout = 3000;
    private final int socketTimeout = 30000;
    private final int connTimeToLive = 120_000;

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getSocketTimeout() {
        return socketTimeout;
    }

    public int getConnTimeToLive() {
        return connTimeToLive;
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public HttpClient httpClient() {
        RequestConfig build = RequestConfig.custom().
                setConnectTimeout(getConnectTimeout()).
                setSocketTimeout(getSocketTimeout()).
                build();
        return HttpClients.custom().
                setConnectionTimeToLive(getConnTimeToLive(), TimeUnit.MILLISECONDS).
                setDefaultRequestConfig(build).
                build();

    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Executor executor() {
        return Executor.newInstance(httpClient());
    }
}
