package org.embulk.parser.xpath2

import org.embulk.config.Task
import org.embulk.config.Config
import org.embulk.config.ConfigDefault
import org.embulk.parser.xpath2.config.{NamespacesConfig, SchemaConfig}
import org.embulk.spi.time.TimestampParser

trait PluginTask extends Task with TimestampParser.Task {
  @Config("stop_on_invalid_record")
  @ConfigDefault("false")
  def getStopOnInvalidRecord: Boolean

  @Config("root")
  def getRoot: String

  @Config("schema")
  def getSchema: SchemaConfig

  @Config("namespaces")
  def getNamespaces: NamespacesConfig

}

