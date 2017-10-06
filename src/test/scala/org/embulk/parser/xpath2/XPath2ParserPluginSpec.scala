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

  @Test def test() {

    val configSource: ConfigSource = Exec.newConfigSource()
      .set("in", Map[String, String]("type" -> "file", "path_prefix" -> dataPath).asJava)
      .set("root", "/ns1:root/ns2:entry")
      .set("schema", List[util.Map[String, String]](
        Map("path" -> "ns2:id", "name" -> "id", "type" -> "long").asJava,
        Map("path" -> "ns2:title", "name" -> "title", "type" -> "string").asJava,
        Map("path" -> "ns2:meta/ns2:author", "name" -> "author", "type" -> "string").asJava,
        Map("path" -> "ns2:date", "name" -> "date", "type" -> "timestamp", "format" -> "%Y%m%d", "timezone" -> "UTC").asJava,
        Map("path" -> "ns2:list/ns2:value", "name" -> "list", "type" -> "json").asJava,
      ).asJava)
      .set("namespaces", Map[String, String]("ns1" -> "http://example.com/ns1/", "ns2" -> "http://example.com/ns2/").asJava)
      .set("out", Map[String, String]("type" -> "stdout").asJava)

    val task = configSource.loadConfig(classOf[PluginTask])

    var schema: Schema = null

    val plugin = new XPath2ParserPlugin()
    plugin.transaction(configSource, (_: TaskSource, s: Schema) => {schema = s})

    val result: mutable.Buffer[collection.mutable.Map[String, Any]] = mutable.Buffer()

    plugin.run(
      task.dump(),
      schema,
      new InputStreamFileInput(Exec.getBufferAllocator(), new FileInputStream(new File(dataPath))),
      new TransactionalPageOutput() {

        import org.embulk.spi.PageReader

        val reader = new PageReader(schema)

        override def add(page: Page) = {
          reader.setPage(page)

          while (reader.nextRecord()) {
            val record: collection.mutable.Map[String, Any] = collection.mutable.Map()

            schema.getColumns().asScala.foreach { column =>

              column.visit(new ColumnVisitor() {
                override def timestampColumn(column: Column): Unit = {
                  if (reader.isNull(column)) {
                    record.put(column.getName, null)
                  } else {
                    record.put(column.getName, reader.getTimestamp(column))
                  }
                }

                override def stringColumn(column: Column): Unit = {
                  if (reader.isNull(column)) {
                    record.put(column.getName, null)
                  } else {
                    record.put(column.getName, reader.getString(column))
                  }
                }

                override def longColumn(column: Column): Unit = {
                  if (reader.isNull(column)) {
                    record.put(column.getName, null)
                  } else {
                    record.put(column.getName, reader.getLong(column))
                  }
                }

                override def doubleColumn(column: Column): Unit = {
                  if (reader.isNull(column)) {
                    record.put(column.getName, null)
                  } else {
                    record.put(column.getName, reader.getDouble(column))
                  }
                }

                override def booleanColumn(column: Column): Unit = {
                  if (reader.isNull(column)) {
                    record.put(column.getName, null)
                  } else {
                    record.put(column.getName, reader.getBoolean(column))
                  }
                }

                override def jsonColumn(column: Column): Unit = {
                  if (reader.isNull(column)) {
                    record.put(column.getName, null)
                  } else {
                    record.put(column.getName, reader.getJson(column))
                  }
                }
              })


            }
            result += record
          }
        }

        override def commit() = Exec.newTaskReport()
        override def abort() = {}
        override def finish() = {}
        override def close() = {}
      }
    )

    println(result)

    assertEquals(ArrayBuffer(Map("date" -> Timestamp.ofEpochSecond(978307200L), "list" -> new JsonParser().parse("""["a","b","c"]"""), "title" -> "Hello!", "author" -> "maji-KY", "id" -> 1L)), result)
  }

}
