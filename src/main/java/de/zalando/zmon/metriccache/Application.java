package de.zalando.zmon.metriccache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.zalando.zmon.metriccache.restmetrics.AppMetricsService;
import de.zalando.zmon.metriccache.restmetrics.VersionResult;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
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

import javax.servlet.http.HttpServletRequest;
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

    private final MetricCacheConfig config;

    private final ObjectMapper mapper;

    private final ApplicationMetricsWriter metricsWriter;

    private final AppMetricsService applicationRestMetrics;

    private final Executor executor;

    private final Tracer tracer;

    @Autowired
    public Application(MetricCacheConfig config,
                       ObjectMapper mapper,
                       ApplicationMetricsWriter metricsWriter,
                       AppMetricsService applicationRestMetrics,
                       Executor executor,
                       Tracer tracer) {
        this.config = config;
        this.mapper = mapper;
        this.metricsWriter = metricsWriter;
        this.applicationRestMetrics = applicationRestMetrics;
        this.executor = executor;
        this.tracer = tracer;
    }

    @ResponseBody
    @RequestMapping(value = "/api/v1/rest-api-metrics/", method = RequestMethod.POST)
    public void putRestAPIMetrics(HttpServletRequest request,
                                  @RequestBody String data) throws IOException {
        try (Scope ignored = createSpan("add_metrics")) {
            // assume for now, that we only receive the right application data
            List<CheckData> results = mapper.readValue(data, new TypeReference<List<CheckData>>() {
            });
            applicationRestMetrics.storeData(results);
        }
    }

    @ResponseBody
    @RequestMapping(value = "/api/v1/rest-api-metrics/unpartitioned", method = RequestMethod.POST)
    public void putRestAPIMetricsUnpartitioned(HttpServletRequest request,
                                               @RequestBody String data) throws IOException {
        try (Scope ignored = createSpan("add_metrics_unpartitioned")) {
            // Post data but repartition accross set of hosts (this is already done in data service)
            List<CheckData> results = mapper.readValue(data, new TypeReference<List<CheckData>>() {
            });
            metricsWriter.write(results);
        }
    }

    @ResponseBody
    @RequestMapping(value = "/api/v1/rest-api-metrics/applications", method = RequestMethod.GET)
    public Collection<String> getRegisteredApplications(HttpServletRequest request,
                                                        @RequestParam(value = "global", defaultValue = "false") boolean global) {
        try (Scope ignored = createSpan("get_registered_applications")) {
            // assume for now, that we only receive the right application data
            return applicationRestMetrics.getRegisteredAppVersions();
        }
    }

    @ResponseBody
    @RequestMapping(value = "/api/v1/rest-api-metrics/tracked-endpoints", method = RequestMethod.GET)
    public Collection<String> getTrackedEndpoints(HttpServletRequest request,
                                                  @RequestParam(value = "application_id") String applicationId,
                                                  @RequestParam(value = "global", defaultValue = "false") boolean global) {
        try (Scope scope = createSpan("get_tracked_endpoints")) {
            scope.span().setTag("application_id", applicationId);
            // assume for now, that we only receive the right application data
            return applicationRestMetrics.getRegisteredEndpoints(applicationId);
        }
    }

    @ResponseBody
    @RequestMapping(value = "/api/v1/rest-api-metrics/kairosdb-format", method = RequestMethod.GET)
    public void getMetricsInKairosDBFormat(Writer writer,
                                           HttpServletResponse response,
                                           HttpServletRequest request,
                                           @RequestParam(value = "application_id") String applicationId,
                                           @RequestParam(value = "application_version", defaultValue = "1") String applicationVersion,
                                           @RequestParam(value = "redirect", defaultValue = "true") boolean redirect)
            throws URISyntaxException {
        if (config.getRest_metric_hosts().isEmpty() ||
                (config.getRest_metric_hosts().size() == 1 && config.getRest_metric_hosts().get(0).equals("localhost"))) {
            redirect = false;
        }

        try (Scope scope = createSpan("get_metrics_kairosdb_format")) {
            scope.span().setTag("application_id", applicationId);

            if (!redirect) {
                response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
                AppMetricsService.KairosDBResultWrapper kairosResult =
                        applicationRestMetrics.getKairosResult(applicationId, applicationVersion, System.currentTimeMillis());
                writer.write(mapper.writeValueAsString(kairosResult));
            } else {
                makeRedirect(writer, response, applicationId, applicationVersion,
                        "/api/v1/rest-api-metrics/kairosdb-format");
            }
        } catch (IOException ex) {
            LOG.error("Failed to write metric result to output stream", ex);
            // throw ex;
        }
    }


    @ResponseBody
    @RequestMapping(value = "/api/v1/rest-api-metrics/", method = RequestMethod.GET)
    public void getRestApiMetrics(Writer writer,
                                  HttpServletResponse response,
                                  HttpServletRequest request,
                                  @RequestParam(value = "application_id") String applicationId,
                                  @RequestParam(value = "application_version") String applicationVersion,
                                  @RequestParam(value = "redirect", defaultValue = "true") boolean redirect)
            throws URISyntaxException, IOException {
        redirect = isRedirect(redirect);

        try (Scope scope = createSpan("get_metrics")) {
            scope.span().setTag("application_id", applicationId);

            if (redirect) {
                scope.span().setTag("redirect", true);
                makeRedirect(writer, response, applicationId, applicationVersion,
                        "/api/v1/rest-api-metrics/");
            } else {
                response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
                VersionResult metrics = applicationRestMetrics.getAggrMetrics(
                        applicationId, applicationVersion, System.currentTimeMillis()
                );
                writer.write(mapper.writeValueAsString(metrics));
            }
        } catch (IOException ex) {
            LOG.error("Failed to write metric result to output stream", ex);
            throw ex;
        }
    }

    private boolean isRedirect(boolean redirectRequested) {
        final boolean noHostsToRedirect = config.getRest_metric_hosts().isEmpty();
        final boolean onlyLocalhostToRedirect = config.getRest_metric_hosts().size() == 1 && config.getRest_metric_hosts().get(0).equals("localhost");
        return !noHostsToRedirect && !onlyLocalhostToRedirect && redirectRequested;
    }

    private void makeRedirect(Writer writer, HttpServletResponse response,
                              String applicationId, String applicationVersion, String path)
            throws URISyntaxException, IOException {
        int hostId = Math.abs(applicationId.hashCode() % config.getRest_metric_hosts().size());
        String targetHost = config.getRest_metric_hosts().get(hostId);
        LOG.info("Redirecting metrics request to {} = {}/{}", applicationId, hostId, targetHost);

        URIBuilder builder = new URIBuilder();
        URI uri = builder.setScheme("http")
                .setHost(targetHost)
                .setPort(Integer.parseInt(config.getServer_port()))
                .setPath(path)
                .setParameter("redirect", "false")
                .setParameter("application_id", applicationId)
                .setParameter("application_version", applicationVersion).build();

        String body = executor.execute(Request.Get(uri)).returnContent().asString();
        response.setContentType(ContentType.APPLICATION_JSON.getMimeType());
        writer.write(body);
    }


    private Scope createSpan(final String operationName) {
        final Span span = tracer.activeSpan();
        span.setOperationName(operationName);
        span.setTag(Tags.SPAN_KIND.getKey(), Tags.SPAN_KIND_SERVER);
        return tracer.scopeManager().activate(span, true);
    }

    public static void main(String[] args) {
        try {
            SpringApplication.run(Application.class, args);
        } catch (Exception e) {
            LOG.error("Error occurred on startup", e);
        }
    }
}
