package de.zalando.zmon.metriccache

import java.util
import java.util.Collections

import io.opentracing.contrib.spring.web.interceptor.HandlerInterceptorSpanDecorator
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.stereotype.Component

import scala.beans.BeanProperty

@Component
@Configuration
@ConfigurationProperties(prefix = "metriccache")
class MetricCacheConfig {

  @BeanProperty var rest_metric_hosts: java.util.List[String] = new util.ArrayList[String]()

  @Value("${server.port}")
  @BeanProperty var server_port: String = _

  @Bean def interceptorSpanDecorator(): util.List[HandlerInterceptorSpanDecorator] = Collections.emptyList()
}