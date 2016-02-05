package de.zalando.zmon.metriccache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import de.zalando.zmon.metriccache.restmetrics.AppMetricsService;
import org.apache.http.client.fluent.Executor;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;

/**
 * Created by jmussler on 4/21/15.
 */
@RestController
@EnableAutoConfiguration
@EnableConfigurationProperties
@Configuration
@ComponentScan
public class Application {

    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    @Autowired
    MetricCacheConfig config;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    ApplicationMetricsWriter metricsWriter;

    @Autowired
    AppMetricsService applicationRestMetrics;

    private static ObjectMapper valueMapper;
    static {
        valueMapper = new ObjectMapper();
        valueMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        valueMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }


    @ResponseBody
    @RequestMapping(value="/api/v1/rest-api-metrics/", method=RequestMethod.POST)
    public void putRestAPIMetrics(@RequestBody String data) throws IOException {
        // assume for now, that we only receive the right application data
        List<CheckData> results = mapper.readValue(data, new TypeReference<List<CheckData>>(){});
        applicationRestMetrics.storeData(results);
    }

    @ResponseBody
    @RequestMapping(value="/api/v1/rest-api-metrics/unpartitioned", method=RequestMethod.POST)
    public void putRestAPIMetricsUnpartitioned(@RequestBody String data) throws IOException {
        // Post data but repartition accross set of hosts (this is already done in data service)
        List<CheckData> results = mapper.readValue(data, new TypeReference<List<CheckData>>(){});
        metricsWriter.write(results);
    }

    @ResponseBody
    @RequestMapping(value="/api/v1/rest-api-metrics/applications", method=RequestMethod.GET)
    public Collection<String> getRegisteredApplications(@RequestParam(value="global", defaultValue="false") boolean global) throws IOException {
        // assume for now, that we only receive the right application data
        return applicationRestMetrics.getRegisteredAppVersions();
    }

    @ResponseBody
    @RequestMapping(value="/api/v1/rest-api-metrics/tracked-endpoints", method=RequestMethod.GET)
    public Collection<String> getRegisteredApplications(@RequestParam(value="application_id") String applicationId, @RequestParam(value="global", defaultValue="false") boolean global) throws IOException {
        // assume for now, that we only receive the right application data
        return applicationRestMetrics.getRegisteredEndpoints(applicationId);
    }

    @ResponseBody
    @RequestMapping(value="/api/v1/rest-api-metrics/kairosdb-format", method=RequestMethod.GET)
    public void getMetricsInKairosDBFormat(Writer writer, HttpServletResponse response, @RequestParam(value="application_id") String applicationId, @RequestParam(value="application_version", defaultValue="1") String applicationVersion, @RequestParam(value="redirect", defaultValue="true") boolean redirect) throws URISyntaxException, IOException {
        if(!redirect) {
            try {
                response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
                writer.write(mapper.writeValueAsString(applicationRestMetrics.getKairosResult(applicationId, applicationVersion, System.currentTimeMillis())));
            } catch (IOException ex) {
                LOG.error("Failed to write metric result to output stream", ex);
            }
        }
        else {
            int hostId = Math.abs(applicationId.hashCode() % config.getRest_metric_hosts().size());
            String targetHost = config.getRest_metric_hosts().get(hostId);
            // TODO: we would not need to redirect if the host list is empty or contains only one item (ourself)
            LOG.info("Redirecting KairosDB metrics request to {} = {}/{}", applicationId, hostId, targetHost);

            Executor executor = Executor.newInstance();
            URIBuilder builder = new URIBuilder();
            URI uri = builder.setScheme("http").setHost(targetHost).setPort(Integer.parseInt(config.getServer_port())).setPath("/api/v1/rest-api-metrics/kairosdb-format").setParameter("redirect", "false")
                    .setParameter("application_id", applicationId)
                    .setParameter("application_version", applicationVersion).build();

            String body = executor.execute(Request.Get(uri)).returnContent().asString();
            response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            writer.write(body);
        }
    }

    @ResponseBody
    @RequestMapping(value="/api/v1/rest-api-metrics/", method=RequestMethod.GET)
    public void getRestApiMetrics(Writer writer, HttpServletResponse response, @RequestParam(value="application_id") String applicationId, @RequestParam(value="application_version") String applicationVersion, @RequestParam(value="redirect", defaultValue="true") boolean redirect) throws URISyntaxException, IOException {
        if(!redirect) {
            try {
                response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
                writer.write(mapper.writeValueAsString(applicationRestMetrics.getAggrMetrics(applicationId, applicationVersion, System.currentTimeMillis())));
            } catch (IOException ex) {
                LOG.error("Failed to write metric result to output stream", ex);
            }
        }
        else {
            int hostId = Math.abs(applicationId.hashCode() % config.getRest_metric_hosts().size());
            String targetHost = config.getRest_metric_hosts().get(hostId);
            LOG.info("Redirecting metrics request to {} = {}/{}", applicationId, hostId, targetHost);

            Executor executor = Executor.newInstance();
            URIBuilder builder = new URIBuilder();
            URI uri = builder.setScheme("http").setHost(targetHost).setPort(Integer.parseInt(config.getServer_port())).setPath("/api/v1/rest-api-metrics/").setParameter("redirect", "false")
                                                                                      .setParameter("application_id", applicationId)
                                                                                      .setParameter("application_version", applicationVersion).build();

            String body = executor.execute(Request.Get(uri)).returnContent().asString();
            response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
            writer.write(body);
        }
    }

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class, args);
    }
}
