package de.zalando.zmon.metriccache.restmetrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.zalando.zmon.metriccache.CheckData;
import de.zalando.zmon.metriccache.MetricCacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

/**
 * Created by jmussler on 05.12.15.
 */

@Service
public class AppMetricsService {

    private static final Logger LOG = LoggerFactory.getLogger(AppMetricsService.class);

    private final List<String> serviceHosts;
    private final int serverPort;

    private HashMap<String, ApplicationVersion> appVersions = new HashMap<>();

    private final String localHostName;
    private final boolean forwardData;
    private int localPartition = -1;

    private final static ObjectMapper mapper = new ObjectMapper();

    private final ExecutorService asyncExecutorPool = Executors.newFixedThreadPool(5);

    private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1);

    private static class CleanUpJob implements Runnable {

        private final AppMetricsService appMetricService;

        CleanUpJob(AppMetricsService aMS) {
            appMetricService = aMS;
        }

        public void run() {
            try {
                appMetricService.cleanUp();
            }
            catch(Exception ex) {
                LOG.error("Unexpected error in cleanup job");
            }
        }
    }

    @Autowired
    public AppMetricsService(MetricCacheConfig config) throws IOException {
        serviceHosts = config.getRestMetricHosts();
        localHostName = InetAddress.getLocalHost().getHostName();
        serverPort = Integer.parseInt(config.getServerPort());

        for(int i = 0; i < serviceHosts.size(); ++i) {
            if(serviceHosts.get(i).equals(localHostName)) {
                localPartition = i;
                break;
            }
        }

        executorService.scheduleWithFixedDelay(new CleanUpJob(this), 60, 60, TimeUnit.MINUTES);

        if (serviceHosts.size()==1 && serviceHosts.get(0).equals("localhost")) {
            forwardData = false;
        }
        else {
            forwardData = true;
        }

        LOG.info("Setting local partition to {}", localPartition);
        LOG.info("Local host resolves to: {}", localHostName);
        LOG.info("Host names {}", serviceHosts);
    }

    protected void cleanUp() {
        LOG.info("Starting instance cleanup...");
        for(ApplicationVersion v : appVersions.values()) {
            v.cleanUp();
        }
    }

    public Collection<String> getRegisteredAppVersions() {
        return appVersions.keySet();
    }

    public Collection<String> getRegisteredEndpoints(String applicationId) {
        if(!appVersions.containsKey(applicationId)) return null;
        ApplicationVersion v = appVersions.get(applicationId);
        return v.getTrackedEndpoints();
    }

    public void storeData(List<CheckData> data) {
        for(CheckData d: data) {
            Double ts = d.check_result.get("ts").asDouble();
            ts = ts * 1000.;
            Long tsL = ts.longValue();
            pushMetric(d.entity.get("application_id"), d.entity.get("application_version"),d.entity_id, tsL, d.check_result.get("value"));
        }
    }

    public void pushMetric(String applicationId, String applicationVersion, String entityId, long ts, JsonNode checkResult) {
        Iterator<Map.Entry<String, JsonNode>> endpoints = ((ObjectNode)checkResult).fields();
        while(endpoints.hasNext()) {
            Map.Entry<String, JsonNode> endpoint = endpoints.next();
            String path = endpoint.getKey();

            Iterator<Map.Entry<String, JsonNode>> methods = ((ObjectNode) endpoint.getValue()).fields();
            while(methods.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methods.next();
                String method = methodEntry.getKey();

                Iterator<Map.Entry<String, JsonNode>> statusCodes = ((ObjectNode) methodEntry.getValue()).fields();
                while(statusCodes.hasNext()) {
                    Map.Entry<String, JsonNode> metricEntry = statusCodes.next();
                    if(metricEntry.getValue().has("99th") && metricEntry.getValue().has("mRate")) {
                        int status = 999;
                        try {
                            status = Integer.parseInt(metricEntry.getKey());
                        }
                        catch(NumberFormatException ex) {
                            if(metricEntry.getKey().startsWith("2")) {
                                status = 299;
                            }
                            else if (metricEntry.getKey().startsWith("4")) {
                                status = 499;
                            }
                            else if (metricEntry.getKey().startsWith("5")) {
                                status = 599;
                            }
                        }

                        storeMetric(applicationId, applicationVersion, entityId, path, method, status,
                                ts,
                                metricEntry.getValue().get("mRate").asDouble(),
                                metricEntry.getValue().get("median").asDouble(),
                                metricEntry.getValue().get("75th").asDouble(),
                                metricEntry.getValue().get("99th").asDouble());
                    }
                }
            }
        }
    }

    public static class KairosDBResultWrapper  {
        public final List<Object> queries = new ArrayList<>(1);

        public KairosDBResultWrapper() {

        }
    }

    public static class KairosDBResultQuery {
        public final List<ObjectNode> results = new ArrayList<>();

        public KairosDBResultQuery() {
        }

        public ObjectNode addResult(String applicationId, String key, String metric, String sc, String sg) {
            ObjectNode o = mapper.createObjectNode();
            results.add(o);

            ObjectNode tags = o.putObject("tags");
            tags.putArray("key").add(key);
            tags.putArray("metric").add(metric);
            tags.putArray("application_id").add(applicationId);
            tags.putArray("sg").add(sg);
            tags.putArray("sc").add(sc);

            o.putArray("values");

            return o;
        }
    }

    public static KairosDBResultWrapper convertToKairosDB(String applicationId, VersionResult data) {
        KairosDBResultWrapper w = new KairosDBResultWrapper();
        KairosDBResultQuery q = new KairosDBResultQuery();
        w.queries.add(q);

        for(EpResult er : data.endpoints.values()) {
            for(Map.Entry<Integer, List<EpPoint>> p : er.points.entrySet()) {
                if(p.getValue().size()<=0) {
                    continue;
                }

                ObjectNode r = q.addResult(applicationId, er.path+"."+er.method+"."+p.getKey()+".mRate", "mRate", p.getKey().toString(), p.getKey().toString().substring(0,1));
                for(EpPoint dp : p.getValue()) {
                    ArrayNode a = mapper.createArrayNode();
                    a.add(dp.ts).add(dp.rate);
                    ((ArrayNode)r.get("values")).add(a);
                }

                r = q.addResult(applicationId, er.path+"."+er.method+"."+p.getKey()+".median", "median", p.getKey().toString(), p.getKey().toString().substring(0,1));
                for(EpPoint dp : p.getValue()) {
                    ArrayNode a = mapper.createArrayNode();
                    a.add(dp.ts).add(dp.latencyMedian);
                    ((ArrayNode)r.get("values")).add(a);
                }

                r = q.addResult(applicationId, er.path+"."+er.method+"."+p.getKey()+".75th", "75th", p.getKey().toString(), p.getKey().toString().substring(0,1));
                for(EpPoint dp : p.getValue()) {
                    ArrayNode a = mapper.createArrayNode();
                    a.add(dp.ts).add(dp.latency75th);
                    ((ArrayNode)r.get("values")).add(a);
                }

                r = q.addResult(applicationId, er.path+"."+er.method+"."+p.getKey()+".99th", "99th", p.getKey().toString(), p.getKey().toString().substring(0,1));
                for(EpPoint dp : p.getValue()) {
                    ArrayNode a = mapper.createArrayNode();
                    a.add(dp.ts).add(dp.latency);
                    ((ArrayNode)r.get("values")).add(a);
                }

                r = q.addResult(applicationId, er.path+"."+er.method+"."+p.getKey()+".min", "min", p.getKey().toString(), p.getKey().toString().substring(0,1));
                for(EpPoint dp : p.getValue()) {
                    ArrayNode a = mapper.createArrayNode();
                    a.add(dp.ts).add(dp.minLatency);
                    ((ArrayNode)r.get("values")).add(a);
                }

                r = q.addResult(applicationId, er.path+"."+er.method+"."+p.getKey()+".max", "max", p.getKey().toString(), p.getKey().toString().substring(0,1));
                for(EpPoint dp : p.getValue()) {
                    ArrayNode a = mapper.createArrayNode();
                    a.add(dp.ts).add(dp.maxLatency);
                    ((ArrayNode)r.get("values")).add(a);
                }
            }
        }

        return w;
    }

    public KairosDBResultWrapper getKairosResult(String applicationId, String applicationVersion, long maxTs) {
        if(!appVersions.containsKey(applicationId)) {
            return null;
        }

        VersionResult data = appVersions.get(applicationId).getData(maxTs);
        return convertToKairosDB(applicationId, data);
    }


    public VersionResult getAggrMetrics(String applicationId, String applicationVersion, long maxTs) {
        if(!appVersions.containsKey(applicationId)) {
            return null;
        }
        return appVersions.get(applicationId).getData(maxTs);
    }

    private void storeMetric(String applicationId, String applicationVersion, String entityId, String path, String method, int status, long ts, double rate, double latencyMedian, double latency75th, double latency99th) {
        ApplicationVersion v = appVersions.get(applicationId); // no versioning for now
        if(null==v) {
            synchronized (this) {
                v = appVersions.get(applicationId);
                if(null==v) {
                    LOG.info("Adding application version {} {}", applicationId, applicationVersion);
                    v = new ApplicationVersion(applicationId, applicationVersion);
                    appVersions.put(applicationId, v);
                }
            }
        }
        v.addDataPoint(entityId, path, method, status, ts, rate, latencyMedian, latency75th, latency99th);
    }
}
