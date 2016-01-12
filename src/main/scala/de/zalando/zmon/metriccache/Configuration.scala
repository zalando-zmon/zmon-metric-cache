package de.zalando.zmon.metriccache

import java.util

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

import scala.beans.BeanProperty

@Component
@Configuration
@ConfigurationProperties(prefix = "metriccache")
class DataServiceConfig {

  @BeanProperty var rest_metric_hosts : java.util.List[String] = new util.ArrayList[String]()

  @Value("${server.port}")
  @BeanProperty var server_port : String = null
}