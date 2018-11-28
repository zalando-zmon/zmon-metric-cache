package de.zalando.zmon.metriccache

import java.util
import java.util.{Collections, List}

import io.opentracing.contrib.spring.web.interceptor.HandlerInterceptorSpanDecorator
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.stereotype.Component
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter

import scala.beans.BeanProperty

@Component
@MetricCacheConfig
@ConfigurationProperties(prefix = "metriccache")
class MetricCacheConfig {

  @BeanProperty var rest_metric_hosts : java.util.List[String] = new util.ArrayList[String]()

  @Value("${server.port}")
  @BeanProperty var server_port : String = _

  @Bean def interceptorSpanDecorator(): ObjectProvider[util.List[HandlerInterceptorSpanDecorator]] =
    new ObjectProvider[util.List[HandlerInterceptorSpanDecorator]] {
    override def getObject(args: Any*): util.List[HandlerInterceptorSpanDecorator] = getObject

    override def getIfAvailable: util.List[HandlerInterceptorSpanDecorator] = getObject

    override def getIfUnique: util.List[HandlerInterceptorSpanDecorator] = getObject

    override def getObject: util.List[HandlerInterceptorSpanDecorator] = Collections.emptyList()
  }
}