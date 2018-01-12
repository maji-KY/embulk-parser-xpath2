package org.embulk.parser.xpath2

import com.ximpleware.VTDNav

object VTD {

  final def withinContext[A](nav: VTDNav)(f: => A): A = try {
    nav.push()
    f
  } finally nav.pop()

}
