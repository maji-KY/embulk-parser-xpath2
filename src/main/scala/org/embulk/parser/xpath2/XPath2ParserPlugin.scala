package org.embulk.parser.xpath2

import java.util
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.{DocumentBuilder, DocumentBuilderFactory}
import javax.xml.xpath.{XPathConstants, XPathExpression, XPathFactory}

import org.embulk.config._
import org.embulk.parser.xpath2.config.ColumnConfig
import org.embulk.spi._
import org.embulk.spi.`type`._
import org.embulk.spi.time.TimestampParser
import org.embulk.spi.util.FileInputInputStream
import org.slf4j.Logger
import org.w3c.dom.{Document, Node, NodeList}

import scala.collection.JavaConverters._
import scala.collection.immutable
import scala.util.control.NonFatal

class XPath2ParserPlugin extends ParserPlugin {

  val logger: Logger = Exec.getLogger(classOf[XPath2ParserPlugin])

  def docBuilder: DocumentBuilder = {
    val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance
    factory.setNamespaceAware(true)
    factory.newDocumentBuilder()
  }

  override def transaction(config: ConfigSource, control: ParserPlugin.Control): Unit = {
    val task = config.loadConfig(classOf[PluginTask])
    control.run(
      task.dump(),
      new Schema(task.getSchema.columns.asScala.zipWithIndex.map { case (x, idx) => new Column(idx, x.name, x.`type`) }.asJava)
    )
  }

  override def run(taskSource: TaskSource, schema: Schema, input: FileInput, output: PageOutput): Unit = {

    val task: PluginTask = taskSource.loadTask(classOf[PluginTask])
    val stopOnInvalidRecord: Boolean = task.getStopOnInvalidRecord

    val xPathInstance = XPathFactory.newInstance.newXPath()
    xPathInstance.setNamespaceContext(new NamespaceContext {
      override def getPrefix(namespaceURI: String): String = task.getNamespaces.conf.asScala.collectFirst { case (_, v) if v == namespaceURI => v }.orNull
      override def getPrefixes(namespaceURI: String): util.Iterator[_] = task.getNamespaces.conf.asScala.keys.asJava.iterator()
      override def getNamespaceURI(prefix: String): String = task.getNamespaces.conf.asScala(prefix)
    })

    val rootXPath: XPathExpression = xPathInstance.compile(task.getRoot)
    val columnXPaths: immutable.Seq[XPathExpression] = task.getSchema.columns.asScala.map(x => xPathInstance.compile(x.path)).toList

    val timestampParsers: Map[String, TimestampParser] = task.getSchema.columns.asScala
      .collect { case ColumnConfig(_, name, _, Some(timestampColumnOption), _) => (name, new TimestampParser(task, timestampColumnOption)) }.toMap

    LoanPattern(new PageBuilder(Exec.getBufferAllocator, schema, output)) { pb =>
      while (input.nextFile()) {
        parseXML(input) match {
          case Right(doc) =>
            val rootNodes = rootXPath.evaluate(doc, XPathConstants.NODESET).asInstanceOf[NodeList]
            (0 until rootNodes.getLength).map(rootNodes.item).foreach { node =>
              columnXPaths.zipWithIndex.foreach { case (xPath, idx) =>
                val value: Node = xPath.evaluate(node, XPathConstants.NODE).asInstanceOf[Node]
                val column = schema.getColumn(idx)
                if (value == null) {
                  pb.setNull(column)
                } else {
                  setColumn(pb, column, value.getTextContent, timestampParsers)
                }
              }
              pb.addRecord()
            }
          case Left(e) =>
            if(stopOnInvalidRecord) {
              throw new DataException(e)
            } else {
              logger.warn(s"Skipped invalid record $e")
            }
        }
        pb.flush()
      }
      pb.finish()
      pb.close()
    }
  }

  def parseXML(input: FileInput): Either[Throwable, Document] = {
    val stream = new FileInputInputStream(input)
    try {
      Right(docBuilder.parse(stream))
    } catch {
      case NonFatal(e) => Left(e)
    }
  }

  def setColumn(pb: PageBuilder, column: Column, value: String, timestampParsers: Map[String, TimestampParser]): Unit = column.getType match {
    case _: StringType => pb.setString(column, value)
    case _: LongType => pb.setLong(column, value.toLong)
    case _: DoubleType => pb.setDouble(column, value.toDouble)
    case _: BooleanType => pb.setBoolean(column, value.toBoolean)
    case _: JsonType => pb.setString(column, value) // treat json as string.
    case _: TimestampType => pb.setTimestamp(column, timestampParsers(column.getName).parse(value))
  }

}
