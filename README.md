ZMON Metric Cache
=================

Cache for actuator metric data to power the ZMON Frontend's cloud/app view with data.

```bash
./mvnw clean install
scm-source -f target/scm-source.json
docker build -t zmon-metric-cache .
```
