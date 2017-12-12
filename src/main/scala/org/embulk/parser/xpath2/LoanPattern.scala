package org.embulk.parser.xpath2

import scala.util.control.Exception.ignoring

object LoanPattern {

  def apply[R <: AutoCloseable, A](resource: R)(f: R => A): A = {
    try {
      f(resource)
    } finally {
      ignoring(classOf[Throwable]) apply {
        resource.close()
      }
    }
  }

}
