package core.generator

import core.Model

case class Play2Json(sd: ScalaServiceDescription) {

  def readers(model: ScalaModel): String = {
    Seq(
      s"implicit def jsonReads${sd.name}${model.name}: play.api.libs.json.Reads[${model.name}] = {",
      s"  import play.api.libs.json._",
      s"  import play.api.libs.functional.syntax._",
      s"  (",
      model.fields.map { field =>
        val base = s"""    (__ \\ "${field.originalName}").read%s[${field.baseType.name}]"""

        if (field.isOption) {
          base.format("Nullable")
        } else if (field.multiple) {
          base.format("Nullable") + ".map(_.getOrElse(Nil))"
        } else {
          base.format("")
        }
      }.mkString(" and\n"),
      s"  )(${model.name}.apply _)",
      s"}"
    ).mkString("\n")
  }

  def writers(model: ScalaModel): String = {
    Seq(
      s"implicit def jsonWrites${sd.name}${model.name}: play.api.libs.json.Writes[${model.name}] = {",
      s"  import play.api.libs.json._",
      s"  import play.api.libs.functional.syntax._",
      s"  (",
      model.fields.map { field =>
        s"""    (__ \\ "${field.originalName}").write[${field.datatype.name}]"""
      }.mkString(" and\n"),
      s"  )(${model.name}.unapply _)",
      s"}"
    ).mkString("\n")
  }

}
