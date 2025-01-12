package amyc.test

import amyc.parsing._
import org.junit.Test

class ParserTests extends TestSuite with amyc.MainHelpers {
  val pipeline = Lexer andThen Parser andThen treePrinterN("")

  val baseDir = "parser"

  val outputExt = "scala"

  @Test def testLL1 = {
    assert(Parser.program.isLL1)
  }

  @Test def testEmpty = shouldOutput("Empty")
  @Test def testLiterals = shouldOutput("Literals")

  @Test def testExamples = shouldOutput("Examples")

  @Test def testEmptyFile = shouldFail("EmptyFile")
}

