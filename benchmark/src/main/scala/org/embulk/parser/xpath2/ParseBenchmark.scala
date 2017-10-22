package org.embulk.parser.xpath2

import java.io.{InputStream, PipedInputStream, PipedOutputStream}

import org.embulk.EmbulkTestRuntime
import org.embulk.config.TaskSource
import org.embulk.spi.util.InputStreamFileInput
import org.embulk.spi.{Exec, Schema}
import org.openjdk.jmh.annotations.Benchmark

import scala.collection.mutable

class ParseBenchmark  {
  import ParseBenchmark._

  @Benchmark
  def run(): Unit = {
    Exec.doWith(runtime.getExec, () => {
      val configSource = test.configSource
      val task = configSource.loadConfig(classOf[PluginTask])

      var schema: Schema = null

      val plugin = new XPath2ParserPlugin()
      plugin.transaction(configSource, (_: TaskSource, s: Schema) => {schema = s})

      val result: mutable.Buffer[collection.mutable.Map[String, Any]] = mutable.Buffer()

      plugin.run(
        task.dump(),
        schema,
        new InputStreamFileInput(Exec.getBufferAllocator(), testDataInput),
        new TestTransactionalPageOutput(schema, result)
      )

      require(result.size == TestRecordSize)
    })
  }

}

object ParseBenchmark {

  val TestRecordSize = 100 * 1000

  val test = new XPath2ParserPluginSpec()
  val runtime = new EmbulkTestRuntime

  val testDataXmlEntry =
    """    <ns2:entry>
      |        <ns2:id>1</ns2:id>
      |        <ns2:title>Hello!</ns2:title>
      |        <ns2:meta>
      |            <ns2:author>maji-KY</ns2:author>
      |        </ns2:meta>
      |        <ns2:date>20010101</ns2:date>
      |        <ns2:dateTime>2000-12-31 15:00:00</ns2:dateTime>
      |        <ns2:list>
      |            <ns2:value>a</ns2:value>
      |            <ns2:value>b</ns2:value>
      |            <ns2:value>c</ns2:value>
      |        </ns2:list>
      |        <ns2:rating by="subscribers">2.5</ns2:rating>
      |        <ns2:rating>3.5</ns2:rating>
      |        <ns2:released>true</ns2:released>
      |    </ns2:entry>
    """.stripMargin.getBytes

  def testDataInput: InputStream = {
    val header =
      """<?xml version="1.0"?>
        |<ns1:root
        |        xmlns:ns1="http://example.com/ns1/"
        |        xmlns:ns2="http://example.com/ns2/">
      """.stripMargin.getBytes
    val footer =
      """
        |</ns1:root>""".stripMargin.getBytes

    val pipedOut = new PipedOutputStream
    val pipedIn = new PipedInputStream(pipedOut)
    new Thread() {
      override def run(): Unit = {
        pipedOut.write(header)
        1 to TestRecordSize foreach { _ =>
          pipedOut.write(testDataXmlEntry)
        }
        pipedOut.write(footer)
        pipedOut.close()
      }
    }.start()
    pipedIn
  }

  def main(args: Array[String]): Unit = {
    new ParseBenchmark().run()
  }

}
