package macros

import clangast.*
import clangast.given
import clangast.decl.*
import clangast.expr.*
import clangast.expr.binaryop.*
import clangast.expr.unaryop.*
import clangast.stmt.*
import clangast.traversal.CASTMapper
import clangast.types.*

import macros.CompileTree.*
import macros.CompileStatement.*
import macros.CompileDefinition.*
import macros.CompileTerm.*
import macros.CompileType.*

import scala.annotation.tailrec
import scala.quoted.*

object ScalaToC {
  def scalaToCCode[T](expr: Expr[T], funName: Expr[String])(using Quotes): Expr[CASTNode] = {
    import quotes.reflect.*

    println(expr.asTerm.show(using Printer.TreeStructure))

    val cast = compileTree(expr.asTerm, new TranslationContext()) match {
      case funDecl: CFunctionDecl => funDecl.copy(name = funName.value.get)
      case astNode => astNode
    }

    println(cast)
    println(cast.textgen)

    cast.toExpr
  }

  inline def scalaToC(inline funName: String)(inline expr: Any): CASTNode = ${ scalaToCCode('expr, 'funName) }

  def compileAnonFun(f: Expr[_], funName: Expr[String])(using Quotes): Expr[CFunctionDecl] = {
    import quotes.reflect.*

    compileTerm(f.asTerm, new TranslationContext()) match {
      case funDecl: CFunctionDecl => funDecl.copy(name = funName.value.get).toExpr
    }
  }

  def compileExpr(e: Expr[_])(using Quotes): Expr[CExpr] = {
    import quotes.reflect.*

    compileTermToCExpr(e.asTerm, new TranslationContext()).toExpr
  }
  
  def compileType[T](using Quotes, Type[T]): Expr[CType] = {
    import quotes.reflect.*
    
    compileTypeRepr(TypeRepr.of[T], new TranslationContext()).toExpr
  }
}
