package org.embulk.parser.xpath2.config

import com.fasterxml.jackson.annotation.{JsonCreator, JsonValue}
import org.embulk.config.ConfigSource

import scala.collection.JavaConverters._

case class NamespacesConfig(conf: java.util.Map[String, String], src: ConfigSource) {
  @JsonCreator()
  def this(src: ConfigSource) = this(src.getAttributeNames.asScala.map(k => (k, src.get(classOf[String], k))).toMap.asJava, src)
  @JsonValue()
  def getConfigSource: ConfigSource = src.deepCopy()
}
