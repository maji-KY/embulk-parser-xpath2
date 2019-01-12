package org.embulk.parser.xpath2.config

import java.util

import com.fasterxml.jackson.annotation.{JsonCreator, JsonProperty, JsonValue}
import com.google.common.base.Optional
import org.embulk.config.{Config, ConfigDefault, ConfigSource}
import org.embulk.spi.`type`.{JsonType, TimestampType, Type}
import org.embulk.spi.time.TimestampParser.TimestampColumnOption

case class SchemaConfig @JsonCreator()(columns: java.util.List[ColumnConfig]) {
  @JsonValue()
  def getColumns: util.List[ColumnConfig] = columns
}

case class ColumnConfig(path: String, name: String, `type`: Type, timestampOption: Option[TimestampColumnOption], jsonOption: Option[JsonColumnOption], option: ConfigSource) {

  @JsonCreator()
  def this(src: ConfigSource) = {
    this(
      src.get(classOf[String], "path"),
      src.get(classOf[String], "name"),
      src.get(classOf[Type], "type"),
      ColumnConfig.getTimestampOption(src, src.get(classOf[Type], "type")),
      ColumnConfig.getJsonOption(src, src.get(classOf[Type], "type")),
      src
    )
  }

  @JsonValue()
  def getConfigSource: ConfigSource = option

}

private class TimestampColumnOptionImpl(timezone: Optional[String], format: Optional[String], date: Optional[String]) extends TimestampColumnOption {

  @JsonCreator()
  def this(src: ConfigSource) = {
    this(
      src.get(classOf[Optional[String]], "timezone", Optional.absent[String]()),
      src.get(classOf[Optional[String]], "format", Optional.absent[String]()),
      src.get(classOf[Optional[String]], "date", Optional.absent[String]()))
  }

  @Config("timezone")
  @ConfigDefault("null")
  override val getTimeZoneId = timezone

  @Config("format")
  @ConfigDefault("null")
  override val getFormat = format

  @Config("date")
  @ConfigDefault("null")
  override val getDate = date
}

class JsonStructureElement(@JsonProperty("path") val path: String, @JsonProperty("type") val `type`: String) {
  @JsonProperty("name")
  val name: String = path
}

case class JsonColumnOption(@JsonProperty("structure") structure: java.util.List[JsonStructureElement])

object ColumnConfig {
  private def getTimestampOption(src: ConfigSource, `type`: Type): Option[TimestampColumnOption] = `type` match {
    case _: TimestampType => Some(getOption(src).loadConfig(classOf[TimestampColumnOptionImpl]))
    case _ => None
  }

  private def getJsonOption(src: ConfigSource, `type`: Type): Option[JsonColumnOption] = `type` match {
    case _: JsonType =>
      val option = getOption(src)
      if (option.has("structure")) Some(option.loadConfig(classOf[JsonColumnOption])) else None
    case _ => None
  }

  private def getOption(src: ConfigSource) = src.deepCopy().remove("path").remove("name").remove("type")
}
