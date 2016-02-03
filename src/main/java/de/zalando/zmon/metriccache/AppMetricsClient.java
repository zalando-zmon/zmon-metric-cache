package de.zalando.zmon.metriccache;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.fluent.Async;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by jmussler on 05.12.15.
 */

@Service
public class AppMetricsClient {

    private static final Logger LOG = LoggerFactory.getLogger(AppMetricsClient.class);

    private final List<String> serviceHosts;
    private final int serverPort;

    private final ObjectMapper mapper;

    private final ExecutorService asyncExecutorPool = Executors.newFixedThreadPool(5);

    @Autowired
    public AppMetricsClient(MetricCacheConfig config) {
        serviceHosts = config.getRest_metric_hosts();
        serverPort = Integer.parseInt(config.getServer_port());
        this.mapper = new ObjectMapper();

        LOG.info("App metric cache config: hosts {} port {}", serviceHosts, serverPort);
    }

    public void receiveData(Map<Integer, List<CheckData>> data) {
        Async async = Async.newInstance().use(asyncExecutorPool);
        for (int i = 0; i < serviceHosts.size(); ++i) {
            if (!data.containsKey(i) || data.get(i).size() <= 0)
                continue;

            try {
                Request r = Request
                        .Post("http://" + serviceHosts.get(i) + ":" + serverPort + "/api/v1/rest-api-metrics/")
                        .bodyString(mapper.writeValueAsString(data.get(i)), ContentType.APPLICATION_JSON);
                async.execute(r);
            } catch (IOException ex) {
                LOG.error("Failed to serialize check data", ex);
            }
        }
    }
}
