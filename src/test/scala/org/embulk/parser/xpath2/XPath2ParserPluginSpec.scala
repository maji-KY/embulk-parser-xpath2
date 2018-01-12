package org.embulk.parser.xpath2

import java.io.{File, FileInputStream}
import java.util

import org.embulk.EmbulkTestRuntime
import org.embulk.config.{ConfigSource, TaskSource}
import org.embulk.spi._
import org.embulk.spi.json.JsonParser
import org.embulk.spi.time.Timestamp
import org.embulk.spi.util.InputStreamFileInput
import org.junit.Assert._
import org.junit.{Rule, Test}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class XPath2ParserPluginSpec {

  @Rule
  def runtime = new EmbulkTestRuntime

  val dataPath: String = classOf[XPath2ParserPlugin].getClassLoader.getResource("data.xml").getPath
  val invalidDataPath: String = classOf[XPath2ParserPlugin].getClassLoader.getResource("invalid-data.xml").getPath

  def configSource: ConfigSource = Exec.newConfigSource()
    .set("in", Map[String, String]("type" -> "file", "path_prefix" -> dataPath).asJava)
    .set("root", "/ns1:root/ns2:entry")
    .set("schema", List[util.Map[String, String]](
      Map("path" -> "ns2:id", "name" -> "id", "type" -> "long").asJava,
      Map("path" -> "ns2:title", "name" -> "title", "type" -> "string").asJava,
      Map("path" -> "ns2:meta/ns2:author", "name" -> "author", "type" -> "string").asJava,
      Map("path" -> "ns2:date", "name" -> "date", "type" -> "timestamp", "format" -> "%Y%m%d", "timezone" -> "Asia/Tokyo").asJava,
      Map("path" -> "ns2:dateTime", "name" -> "date_time", "type" -> "timestamp", "format" -> "%Y-%m-%d %H:%M:%S", "timezone" -> "UTC").asJava,
      Map("path" -> "ns2:list/ns2:value", "name" -> "list", "type" -> "json").asJava,
      Map("path" -> "ns2:rating[@by='subscribers']", "name" -> "rating_sub", "type" -> "double").asJava,
      Map("path" -> "ns2:released", "name" -> "released", "type" -> "boolean").asJava,
    ).asJava)
    .set("namespaces", Map[String, String]("ns1" -> "http://example.com/ns1/", "ns2" -> "http://example.com/ns2/").asJava)
    .set("out", Map[String, String]("type" -> "stdout").asJava)

  @Test def testParseXML() {

    val cs = configSource
    val task = cs.loadConfig(classOf[PluginTask])

    var schema: Schema = null

    val plugin = new XPath2ParserPlugin()
    plugin.transaction(cs, (_: TaskSource, s: Schema) => {schema = s})

    val result: mutable.Buffer[collection.mutable.Map[String, Any]] = mutable.Buffer()

    plugin.run(
      task.dump(),
      schema,
      new InputStreamFileInput(Exec.getBufferAllocator(), new FileInputStream(new File(dataPath))),
      new TestTransactionalPageOutput(schema, result)
    )

    println(result)

    assertEquals(ArrayBuffer(
      Map(
        "id" -> 1L,
        "title" -> "Hello!",
        "author" -> "maji-KY",
        "date" -> Timestamp.ofEpochSecond(978274800L),
        "date_time" -> Timestamp.ofEpochSecond(978274800L),
        "list" -> new JsonParser().parse("""["a","b","c"]"""),
        "rating_sub" -> 2.5d,
        "released" -> true,
      ),
      Map(
        "id" -> 2L,
        "title" -> "Bonjour!",
        "author" -> "maji-KY",
        "date" -> Timestamp.ofEpochSecond(978274800L),
        "date_time" -> null,
        "list" -> new JsonParser().parse("[]"),
        "rating_sub" -> null,
        "released" -> false,
      )
    ), result)
  }

  @Test(expected = classOf[DataException]) def testStopOnInvalid() {

    val cs = configSource.set("stop_on_invalid_record", true)
    val task = cs.loadConfig(classOf[PluginTask])

    var schema: Schema = null

    val plugin = new XPath2ParserPlugin()
    plugin.transaction(cs, (_: TaskSource, s: Schema) => {schema = s})

    val result: mutable.Buffer[collection.mutable.Map[String, Any]] = mutable.Buffer()

    plugin.run(
      task.dump(),
      schema,
      new InputStreamFileInput(Exec.getBufferAllocator(), new FileInputStream(new File(invalidDataPath))),
      new TestTransactionalPageOutput(schema, result)
    )

  }

  @Test() def testSkipOnInvalid() {

    val cs = configSource
    val task = cs.loadConfig(classOf[PluginTask])

    var schema: Schema = null

    val plugin = new XPath2ParserPlugin()
    plugin.transaction(cs, (_: TaskSource, s: Schema) => {schema = s})

    val result: mutable.Buffer[collection.mutable.Map[String, Any]] = mutable.Buffer()

    plugin.run(
      task.dump(),
      schema,
      new InputStreamFileInput(Exec.getBufferAllocator(), new FileInputStream(new File(invalidDataPath))),
      new TestTransactionalPageOutput(schema, result)
    )


    assertEquals(ArrayBuffer(
      Map(
        "id" -> 1L,
        "title" -> "Hello!",
        "author" -> "maji-KY",
        "date" -> Timestamp.ofEpochSecond(978274800L),
        "date_time" -> Timestamp.ofEpochSecond(978274800L),
        "list" -> new JsonParser().parse("""["a","b","c"]"""),
        "rating_sub" -> 2.5d,
        "released" -> true,
      )
    ), result)
  }

}
