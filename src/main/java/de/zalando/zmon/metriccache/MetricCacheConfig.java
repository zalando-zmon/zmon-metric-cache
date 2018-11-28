package de.zalando.zmon.metriccache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties("metriccache")
public class MetricCacheConfig {
    private List<String> restMetricHosts;

    @Value("${server.port}")
    private String serverPort;

    public List<String> getRestMetricHosts() {
        return restMetricHosts;
    }

    public void setRestMetricHosts(List<String> restMetricHosts) {
        this.restMetricHosts = restMetricHosts;
    }

    public String getServerPort() {
        return serverPort;
    }

    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }
}
