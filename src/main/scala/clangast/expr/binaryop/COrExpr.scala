package clangast.expr.binaryop

import clangast.expr.CExpr

case class COrExpr(lhs: CExpr, rhs: CExpr) extends CBinaryOperator {
  val opcode = "||"
}
