package org.embulk.parser.xpath2

import com.google.common.io.ByteStreams
import com.ximpleware.{AutoPilot, VTDGen, VTDNav}
import org.embulk.config._
import org.embulk.parser.xpath2.config.{ColumnConfig, JsonStructureElement}
import org.embulk.spi._
import org.embulk.spi.`type`._
import org.embulk.spi.time.TimestampParser
import org.embulk.spi.util.FileInputInputStream
import org.slf4j.Logger

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

class XPath2ParserPlugin extends ParserPlugin {

  private[this] val logger: Logger = Exec.getLogger(classOf[XPath2ParserPlugin])

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

    val timestampParsers: Map[String, TimestampParser] = task.getSchema.columns.asScala
      .collect { case ColumnConfig(_, name, _, Some(timestampColumnOption), _, _) => (name, TimestampParser.of(task, timestampColumnOption)) }.toMap

    val jsonStructures: Map[String, Seq[JsonStructureElement]] = task.getSchema.columns.asScala
      .collect { case ColumnConfig(_, name, _,  _, Some(jsonColumnOption), _) => (name, jsonColumnOption.structure.asScala) }.toMap

    def declareXPathNS(ap: AutoPilot): Unit = {
      task.getNamespaces.conf.asScala.foreach { case (prefix, namespaceURI) =>
        ap.declareXPathNameSpace(prefix, namespaceURI)
      }
    }

    val vg = new VTDGen

    LoanPattern(new PageBuilder(Exec.getBufferAllocator, schema, output)) { pb =>
      while (input.nextFile()) {
        LoanPattern(new FileInputInputStream(input)) { fiis =>

          vg.setDoc(ByteStreams.toByteArray(fiis))
          vg.parse(true)

          val nav = vg.getNav
          val rootElementAutoPilot = new AutoPilot(nav)
          declareXPathNS(rootElementAutoPilot)

          val columnElementAutoPilots: Seq[AutoPilot] =
            task.getSchema.columns.asScala.map { cc =>
              val columnElementAutoPilot = new AutoPilot(nav)
              declareXPathNS(columnElementAutoPilot)
              columnElementAutoPilot.selectXPath(cc.path)
              columnElementAutoPilot
            }

          @tailrec
          def execEachRecord(rootAp: AutoPilot): Unit = if (rootAp.evalXPath() != -1) {
            nav.push()
            try {
              columnElementAutoPilots.zipWithIndex.foreach { case (columnElementAutoPilot, idx) =>
                VTD.withinContext(nav) {
                  columnElementAutoPilot.resetXPath()
                  val column = schema.getColumn(idx)
                  handleColumn(pb, nav, columnElementAutoPilot, column, timestampParsers, jsonStructures)
                }
              }
              pb.addRecord()
            } catch {
              case NonFatal(e) => if (stopOnInvalidRecord) {
                throw new DataException(e)
              } else {
                logger.warn(s"Skipped invalid record $e")
              }
            }
            nav.pop()
            execEachRecord(rootAp)
          }

          rootElementAutoPilot.selectXPath(task.getRoot)
          execEachRecord(rootElementAutoPilot)
        }

        pb.flush()
      }
      pb.finish()
    }
  }

  final def handleColumn(pb: PageBuilder, nav: VTDNav, columnAp: AutoPilot, column: Column, timestampParsers: Map[String, TimestampParser], jsonStructures: Map[String, Seq[JsonStructureElement]]): Unit = {
    if (column.getType.isInstanceOf[JsonType]) {
      val jsonValue = MsgPackEncoder.encode(nav, columnAp, column, jsonStructures.get(column.getName))
      pb.setJson(column, jsonValue)
    } else {
      if (columnAp.evalXPath() == -1) {
        pb.setNull(column)
      } else {
        val index = nav.getText
        setColumn(pb, column, nav.toString(index), timestampParsers)
      }
    }
  }

  final def setColumn(pb: PageBuilder, column: Column, value: String, timestampParsers: Map[String, TimestampParser]): Unit = column.getType match {
    case _: StringType => pb.setString(column, value)
    case _: LongType => pb.setLong(column, value.toLong)
    case _: DoubleType => pb.setDouble(column, value.toDouble)
    case _: BooleanType => pb.setBoolean(column, value.toBoolean)
    case _: TimestampType => pb.setTimestamp(column, timestampParsers(column.getName).parse(value))
  }

}
