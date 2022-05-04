package clangast.decl
import clangast.types.{CRecordType, CType}

case class CRecordDecl(name: String, fields: List[CFieldDecl]) extends CTypeDecl with CDeclContext {
  override def getTypeForDecl: CType = CRecordType(this)

  override def decls: List[CDecl] = fields

  override def textgen: String = {
    s"""
       |typedef struct {
       |  ${fields.map(_.textgen).mkString("\n  ")}
       |} $name;
    """.strip().stripMargin
  }
}
