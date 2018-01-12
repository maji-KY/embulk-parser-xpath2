package org.embulk.parser.xpath2

import java.io.{File, FileInputStream}
import java.nio.file
import java.nio.file.Paths

import org.embulk.EmbulkTestRuntime
import org.embulk.config.{ConfigLoader, ConfigSource, TaskSource}
import org.embulk.spi.json.JsonParser
import org.embulk.spi.util.InputStreamFileInput
import org.embulk.spi.{Exec, _}
import org.junit.Assert._
import org.junit.{Rule, Test}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

class XPath2ParserPluginJsonSpec {

  @Rule
  def runtime = new EmbulkTestRuntime

  val yamlPath: file.Path = Paths.get(classOf[XPath2ParserPlugin].getClassLoader.getResource("json_config.yml").toURI)
  val dataPath: String = classOf[XPath2ParserPlugin].getClassLoader.getResource("json_data.xml").getPath

  def configSource: ConfigSource = new ConfigLoader(Exec.getModelManager).fromYamlFile(yamlPath.toFile).getNested("in").getNested("parser")

  @Test def testParseJsonArrayXML() {

    val cs = configSource
    val task = cs.loadConfig(classOf[PluginTask])

    var schema: Schema = null

    val plugin = new XPath2ParserPlugin()
    plugin.transaction(cs, (_: TaskSource, s: Schema) => {schema = s})

    val result: mutable.Buffer[collection.mutable.Map[String, Any]] = mutable.Buffer()

    plugin.run(
      task.dump(),
      schema,
      new InputStreamFileInput(Exec.getBufferAllocator, new FileInputStream(new File(dataPath))),
      new TestTransactionalPageOutput(schema, result)
    )

    println(result)

    val expectedJson =
"""{
  "list": [
    {
     "elements": [
       {
         "elementActive": true,
         "elementName": "foo1",
         "elementValue": 1
       },
       {
         "elementActive": false,
         "elementName": "foo2",
         "elementValue": 2
       }
     ]
    },
    {
     "elements": [
       {
         "elementActive": true,
         "elementName": "bar1",
         "elementValue": 3
       }
     ]
    }
  ]
}"""

    assertEquals(ArrayBuffer(
      Map(
        "id" -> 1L,
        "list" -> new JsonParser().parse(expectedJson)
      )
    ), result)
  }

}
