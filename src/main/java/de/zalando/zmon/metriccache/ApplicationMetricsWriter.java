package de.zalando.zmon.metriccache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class ApplicationMetricsWriter {

    private final Logger log = LoggerFactory.getLogger(ApplicationMetricsWriter.class);

    private final AppMetricsClient applicationMetricsClient;

    private final MetricCacheConfig config;

    @Autowired
    ApplicationMetricsWriter(AppMetricsClient applicationMetricsClient, MetricCacheConfig config) {
        this.applicationMetricsClient = applicationMetricsClient;
        this.config = config;
    }

    @Async
    public void write(List<CheckData> data) {
        log.debug("write application-metrics ...");
        if (null!=data && data.size()>0) {
            try {
                Map<Integer, List<CheckData>> partitions =data.stream()
                        .filter(x -> !x.exception)
                        .collect(Collectors.groupingBy(x -> Math
                                .abs(x.entity.get("application_id").hashCode() % config.getRestMetricHosts().size())));

                applicationMetricsClient.receiveData(partitions);
                log.debug("application-metrics written");
            } catch (Exception ex) {
                // TODO, do we have a metric for this error too
                log.error("Failed to write to REST metrics data={}", data, ex);
            }
        }
    }

}
