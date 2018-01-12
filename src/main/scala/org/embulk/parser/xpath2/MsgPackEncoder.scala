package org.embulk.parser.xpath2

import com.ximpleware.{AutoPilot, VTDNav}
import org.embulk.parser.xpath2.config.JsonStructureElement
import org.embulk.spi.Column
import org.msgpack.value.{Value, Variable}

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.collection.immutable.Queue

sealed trait Direction
case object Parent extends Direction
case object Sibling extends Direction
case object Child extends Direction

private case class Path(depth: Int, pathFragments: Seq[String], moveDirection: Direction) {

  def next(depth: Int, elementName: String): Path = {
    if (this.depth > depth) {
      val (rest :+ _) = pathFragments
      Path(depth, rest, Parent)
    } else if (this.depth == depth) {
      val (rest :+ _) = pathFragments
      Path(depth, rest :+ elementName, Sibling)
    } else {
      Path(depth, pathFragments :+ elementName, Child)
    }
  }

  val path: String = pathFragments.mkString("/")

}

object MsgPackEncoder {

  def encode(nav: VTDNav, columnAp: AutoPilot, column: Column, maybeStructure: Option[Seq[JsonStructureElement]]): Value = maybeStructure.map { structure =>
    // complex json array
    val keyValues = Iterator.continually(columnAp.evalXPath()).takeWhile(_ != -1).flatMap { _ =>
      VTD.withinContext(nav) {
        constructJsonMap(nav, columnAp, column, structure).toSeq
      }
    }
    val mergedMap = keyValues.toSeq.groupBy(_._1).map { case (k, v)=>
      val mergedValues = v.flatMap {
        case (_, x: Seq[Any]) => x
        case _ => sys.error("Root element supports array only. Please reconsider the configuration.")
      }
      (k, mergedValues)
    }
    convertToValue(mergedMap)
  } getOrElse {
    // simple string[]
    @tailrec
    def eachJsonValue(cAp: AutoPilot, queue: Queue[Value]): Queue[Value] = if (cAp.evalXPath() != -1) {
      val index = nav.getText
      val nextQueue = if (index != -1) queue :+ new Variable().setStringValue(nav.toString(index)).asStringValue() else queue
      eachJsonValue(cAp, nextQueue)
    } else queue
    asArrayValue(eachJsonValue(columnAp, Queue.empty[Value]))
  }

  private def constructJsonMap(nav: VTDNav, columnAp: AutoPilot, column: Column, structure: Seq[JsonStructureElement]): Map[String, Any] = {

    @tailrec
    def eachElement(eAp: AutoPilot, previousPath: Path, obj: Map[String, Any]): Map[String, Any] = if (eAp.iterate()) {
      val current = previousPath.next(nav.getCurrentDepth, nav.toString(nav.getCurrentIndex))
      if (current.moveDirection == Parent) {
        obj
      } else {
        val updated = structure.find(_.path == current.path).map { x =>
          x.`type` match {
            case "array" =>
              val targetArray = obj.getOrElse(x.name, Queue[Any]()).asInstanceOf[Seq[Any]]
              val childStructure = eachArrayElement(eAp, current)
              obj.updated(x.name, targetArray ++ childStructure)
            case "string" => obj.updated(x.name, nav.toNormalizedString(nav.getText))
            case "long" => obj.updated(x.name, nav.toNormalizedString(nav.getText).toLong)
            case "boolean" => obj.updated(x.name, nav.toNormalizedString(nav.getText).toBoolean)
            case notSupported@_ => sys.error(s"type=$notSupported is notSupported")
          }
        }
        eachElement(eAp, current, updated.getOrElse(obj))
      }
    } else obj

    def isArrayElement(current: Path): Boolean =
      structure.exists(x => x.path == current.path && x.`type` == "array")

    def eachArrayElement(eAp: AutoPilot, previousPath: Path): Seq[Map[String, Any]] = {
      @tailrec
      def loop(eAp: AutoPilot, previousPath: Path, obj: Map[String, Any], elements: Seq[Map[String, Any]]): Seq[Map[String, Any]] = {
        val arrayContent = eachElement(eAp, previousPath, obj)
        val currentPath = previousPath.next(nav.getCurrentDepth, nav.toString(nav.getCurrentIndex))
        if (isArrayElement(currentPath)) {
          loop(eAp, currentPath, obj, elements :+ arrayContent)
        } else elements :+ arrayContent
      }
      loop(eAp, previousPath, Map.empty[String, Any], Queue.empty[Map[String, Any]])
    }

    val eachElementAp = new AutoPilot(nav)
    eachElementAp.selectElement("*")

    val initialPath = Path(-1, Vector.empty, Sibling)

    eachElement(eachElementAp, initialPath, Map[String, Any]())
  }

  private def convertToValue(obj: Map[String, Any]): Value = {
    val map = obj.map {
      case (k, v: Seq[_]) => (asStringValue(k), convertToValue(v))
      case (k, v: Map[_, _]) => (asStringValue(k), convertToValue(v.asInstanceOf[Map[String, Any]]))
      case (k, v: String) => (asStringValue(k), asStringValue(v))
      case (k, v: Boolean) => (asStringValue(k), asBooleanValue(v))
      case (k, v: Long) => (asStringValue(k), asLongValue(v))
      case (k, v) => sys.error(s"can't convert: key=$k, value=$v")
    }
    asMapValue(map)
  }

  private def convertToValue(seq: Seq[Any]): Value = {
    val list = seq.map {
      case v: Seq[_] => convertToValue(v)
      case v: Map[_, _] => convertToValue(v.asInstanceOf[Map[String, Any]])
      case v: String => asStringValue(v)
      case v: Boolean => asBooleanValue(v)
      case v: Long => asLongValue(v)
    }
    asArrayValue(list)
  }

  private final def asStringValue(value: String): Value = new Variable().setStringValue(value).asStringValue()
  private final def asBooleanValue(value: Boolean): Value = new Variable().setBooleanValue(value).asBooleanValue()
  private final def asLongValue(value: Long): Value = new Variable().setIntegerValue(value).asNumberValue()
  private final def asArrayValue(value: Seq[Value]): Value = new Variable().setArrayValue(value.asJava).asArrayValue()
  private final def asMapValue(value: Map[Value, Value]): Value = new Variable().setMapValue(value.asJava).asMapValue()

}
