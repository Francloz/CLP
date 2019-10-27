package amyc
package parsing

import scala.language.implicitConversions

import amyc.ast.NominalTreeModule._
import amyc.utils._
import Tokens._
import TokenKinds._

import scallion.syntactic._

// The parser for Amy
object Parser extends Pipeline[Iterator[Token], Program]
                 with Syntaxes[Token, TokenKind] with Debug[Token, TokenKind]
                 with Operators {
  import Implicits._

  override def getKind(token: Token): TokenKind = TokenKind.of(token)

  val eof: Syntax[Token] = elem(EOFKind)
  def op(string: String): Syntax[String] = accept(OperatorKind(string)) { case OperatorToken(name) => name }
  def kw(string: String): Syntax[Token] = elem(KeywordKind(string))

  implicit def delimiter(string: String): Syntax[Token] = elem(DelimiterKind(string))

  // An entire program (the starting rule for any Amy file).
  lazy val program: Syntax[Program] = many1(many1(module) ~<~ eof).map(ms => Program(ms.flatten.toList).setPos(ms.head.head))

  // A module (i.e., a collection of definitions and an initializer expression)
  lazy val module: Syntax[ModuleDef] = (kw("object") ~ identifier ~ "{" ~ many(definition) ~ opt(expr) ~ "}").map {
    case obj ~ id ~ _ ~ defs ~ body ~ _ => ModuleDef(id, defs.toList, body).setPos(obj)
  }

  // An identifier.
  val identifier: Syntax[String] = accept(IdentifierKind) {
    case IdentifierToken(name) => name
  }

  // An identifier along with its position.
  val identifierPos: Syntax[(String, Position)] = accept(IdentifierKind) {
    case id@IdentifierToken(name) => (name, id.position)
  }

  // A definition within a module.
  lazy val definition: Syntax[ClassOrFunDef] = functionDefinition | abstractClassDefinition | caseClassDefinition
   
  lazy val functionDefinition: Syntax[ClassOrFunDef] = 
    (kw("def") ~ identifierPos ~ "(".skip ~ parameters ~ ")".skip ~ ":".skip ~  typeTree ~ "{".skip ~ expr ~ "}".skip).map {
      case kw ~ id ~ params ~ rtype ~ body => FunDef(id._1, params, rtype, body).setPos(kw)
    }
  
  lazy val abstractClassDefinition: Syntax[ClassOrFunDef]  =
    (kw("abstract") ~ kw("class") ~ identifierPos).map {
      case kw ~ _ ~ id => AbstractClassDef(id._1).setPos(kw)
    } 
  
  lazy val caseClassDefinition: Syntax[ClassOrFunDef] = 
    (kw("case") ~ kw("class") ~ identifierPos ~ "(".skip ~ parameters ~ ")".skip ~ kw("extends") ~ identifier).map {
      case kw ~ _ ~ id ~ params ~ _ ~ parent => CaseClassDef(id._1, getTypes(params), parent).setPos(kw)
    } 

  // A list of parameter definitions.
  lazy val parameters: Syntax[List[ParamDef]] = repsep(parameter, ",").map(_.toList)

  // A parameter definition, i.e., an identifier along with the expected type.
  lazy val parameter: Syntax[ParamDef] = identifier ~ typeParam
  lazy val typeParam: Syntax[TypeTree] = ":".skip ~ typeTree

  // A type expression.
  lazy val typeTree: Syntax[TypeTree] = primitiveType | identifierType

  // A built-in type (such as `Int`).
  val primitiveType: Syntax[TypeTree] = accept(PrimTypeKind) {
    case tk@PrimTypeToken(name) => TypeTree(name match {
      case "Unit" => UnitType
      case "Boolean" => BooleanType
      case "Int" => IntType
      case "String" => StringType
      case _ => throw new java.lang.Error("Unexpected primitive type name: " + name)
    }).setPos(tk)
  }
  
  lazy val qualifiedIdentifier: Syntax[QualifiedName] = (identifier ~ (".".skip ~ identifier).opt).map{
    case mod ~ Some(id : String) => QualifiedName(Some(mod), id))
    case id ~ None => QualifiedName(None, id))
  }
  // A user-defined type (such as `List`).
  lazy val identifierType: Syntax[TypeTree] = qualifiedIdentifier.map {
    case qn => TypeTree(ClassType(qn)
  }

  // An expression.
  // HINT: You can use `operators` to take care of associativity and precedence
  lazy val expr: Syntax[Expr] = recursive { ??? }
  
  lazy val unaryOperation: Syntax[Expr] = 
  ((op("-") | op("!")).opt ~ basic).map {
    case None ~ e => e
    case Some("-") ~ e => Neg(e)
    case Some("!") ~ e => Not(e)
  }
  lazy val binaryOperation: Syntax[Expr] = recursive {
    operators(factor)(
      op("*") | op("/") | op("%") is LeftAssociative,
      op("+") |  op("-") is LeftAssociative,
      op("<") |  op("<=") is LeftAssociative,
      op("&&") is LeftAssociative,
      op("||") is LeftAssociative
    ) {
      case (l, "+",  r) => Plus(l, r)
      case (l, "-",  r) => Minus(l, r)
      case (l, "/",  r) => Div(l, r)
      case (l, "%",  r) => Mod(l, r)
      case (l, "*",  r) => Times(l, r)
      case (l, "<",  r) => LessThan(l, r)
      case (l, "<=", r) => LessEquals(l, r)
      case (l, "&&", r) => And(l, r)
      case (l, "||", r) => Or(l, r)
      case (l, "++", r) => Concat(l, r)
      case (l, "==", r) => Equals(l, r)
    }
  }

  // A literal expression.
  lazy val literal: Syntax[Literal[_]] = endLit | unitLit
  
  lazy val unitLit : Syntax[Literal[_]] = 
    ("(".skip ~ ")").map {
      case _ => UnitLiteral() 
    }
  lazy val endLit : Syntax[Literal[_]] = accept(LiteralKind) {
    case tk@IntLitToken(value) => IntLiteral(value) 
    case tk@StringLitToken(value) => StringLiteral(value)   
    case tk@BoolLitToken(value) => BooleanLiteral(value)   
  }

  // A pattern as part of a mach case.
  lazy val pattern: Syntax[Pattern] = recursive {
    literalPattern | wildPattern | ???
  }

  lazy val literalPattern: Syntax[Pattern] = ???

  lazy val wildPattern: Syntax[Pattern] = ???


  // HINT: It is useful to have a restricted set of expressions that don't include any more operators on the outer level.
  lazy val simpleExpr: Syntax[Expr] = literal.up[Expr] | variableOrCall | ???

  lazy val variableOrCall: Syntax[Expr] = ???


  // TODO: Other definitions.
  //       Feel free to decompose the rules in whatever way convenient.

  lazy val example: Syntax[Expr] = ("(".skip ~ literal.opt ~ ")".skip).map{
    case Some(lit) => lit.up[Expr]
    case None => UnitLiteral()
  }
  lazy val rec1: Syntax[Expr] = example.map {
    case UnitLiteral() => UnitLiteral()
  }
  lazy val rec2: Syntax[Expr] = example.map {
    case IntLiteral(i) => IntLiteral(i)
  }
  
  lazy val rec3: Syntax[Expr] = rec1 | rec2

  // Ensures the grammar is in LL(1), otherwise prints some counterexamples
  lazy val checkLL1: Boolean = {
    if (rec3.isLL1) {
      true
    } else {
      debug(rec3)
      false
    }
  }

  override def run(ctx: Context)(tokens: Iterator[Token]): Program = {
    import ctx.reporter._
    if (!checkLL1) {
      ctx.reporter.fatal("Program grammar is not LL1!")
    }

    program(tokens) match {
      case Parsed(result, rest) => result
      case UnexpectedEnd(rest) => fatal("Unexpected end of input.")
      case UnexpectedToken(token, rest) => fatal("Unexpected token: " + token + ", possible kinds: " + rest.first.map(_.toString).mkString(", "))
    }
  }
}
