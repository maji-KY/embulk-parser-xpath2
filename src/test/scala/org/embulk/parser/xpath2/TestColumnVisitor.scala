package org.embulk.parser.xpath2

import org.embulk.spi.{Column, ColumnVisitor, PageReader}

class TestColumnVisitor(reader: PageReader, record: collection.mutable.Map[String, Any]) extends ColumnVisitor {
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
}
