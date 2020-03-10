ZMON source code on GitHub is no longer in active development. Zalando will no longer actively review issues or merge pull-requests.

ZMON is still being used at Zalando and serves us well for many purposes. We are now deeper into our observability journey and understand better that we need other telemetry sources and tools to elevate our understanding of the systems we operate. We support the [OpenTelemetry](https://opentelemetry.io/) initiative and recommended others starting their journey to begin there.

If members of the community are interested in continuing developing ZMON, consider forking it. Please review the licence before you do.

# ZMON Metric Cache

[![OpenTracing Badge](https://img.shields.io/badge/OpenTracing-enabled-blue.svg)](http://opentracing.io)

Cache for actuator metric data to power the ZMON Frontend's cloud/app view with data.

```bash
./mvnw clean install
scm-source -f target/scm-source.json
docker build -t zmon-metric-cache .
```
