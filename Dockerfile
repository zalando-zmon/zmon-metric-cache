FROM registry.opensource.zalan.do/stups/openjdk:8-42

EXPOSE 8086

COPY target/zmon-metric-cache-1.0-SNAPSHOT.jar /zmon-metric-cache.jar
COPY scm-source.json /

CMD java $JAVA_OPTS $(java-dynamic-memory-opts) -jar /zmon-metric-cache.jar
