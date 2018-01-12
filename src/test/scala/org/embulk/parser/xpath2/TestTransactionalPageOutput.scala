package org.embulk.parser.xpath2

import org.embulk.spi.{Exec, Page, Schema, TransactionalPageOutput}

import scala.collection.mutable
import scala.collection.JavaConverters._

class TestTransactionalPageOutput(schema: Schema, result: mutable.Buffer[collection.mutable.Map[String, Any]])
  extends TransactionalPageOutput {
  import org.embulk.spi.PageReader

  val reader = new PageReader(schema)

  override def add(page: Page) = {
    reader.setPage(page)

    while (reader.nextRecord()) {
      val record: collection.mutable.Map[String, Any] = collection.mutable.Map()

      schema.getColumns().asScala.foreach { column =>
        column.visit(new TestColumnVisitor(reader, record))
      }
      result += record
    }
  }

  override def commit() = Exec.newTaskReport()
  override def abort() = {}
  override def finish() = {}
  override def close() = {}
}
