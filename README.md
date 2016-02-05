ZMON Metric Cache
=================

Cache for actuator metric data to power the ZMON Frontend's cloud/app view with data.

```bash
sudo pip3 install -U scm-source
./mvnw clean install
scm-source
docker build -t zmon-metric-cache .
```
