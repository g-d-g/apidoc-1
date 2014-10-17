package controllers

import com.gilt.apidocgenerator.models.{ServiceDescription, Generator}
import com.gilt.apidocgenerator.models.json._
import com.gilt.apidocgenerator.Client
import lib.Validation
import play.api.mvc._
import play.api.libs.json._
import core.generator.{CodeGenerator, CodeGenTarget}

object Generators extends Controller {

  def get() = Action { request: Request[AnyContent] =>
    Ok(Json.toJson(targets.filter(t => t.codeGenerator.isDefined && t.status != core.generator.Status.Proposal).map(t => t.metaData)))
  }

  def getByKey(key: String) = Action { request: Request[AnyContent] =>
    findGenerator(key) match {
      case Some((target, _)) => Ok(Json.toJson(target.metaData))
      case _ => NotFound
    }
  }

  def postExecuteByKey(key: String) = Action(parse.json) { request: Request[JsValue] =>
    findGenerator(key) match {
      case Some((_, generator)) =>
        request.body.validate[ServiceDescription] match {
          case e: JsError => Conflict(Json.toJson(Validation.error("invalid json document: " + e.toString)))
          case s: JsSuccess[ServiceDescription] => Ok(Json.toJson(generator.generate(s.get)))
        }
      case _ => NotFound
    }
  }

  def findGenerator(key: String): Option[(CodeGenTarget, CodeGenerator)] = for {
    target <- targets.find(_.metaData.key == key)
    codeGenerator <- target.codeGenerator
  } yield(target -> codeGenerator)

  val targets = Seq(
      CodeGenTarget(
        metaData = Generator(
          key = "ruby_client",
          name = "Ruby client",
          description = Some("A pure ruby library to consume api.json web services. The ruby client has minimal dependencies and does not require any additional gems."),
          language = Some("Ruby")
        ),
        status = core.generator.Status.Beta,
        codeGenerator = Some(models.RubyClientGenerator)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "ning_1_8_client",
          name = "Ning Async Http Client 1.8",
          description = Some("Ning Async Http v.18 Client - see https://sonatype.github.io/async-http-client"),
          language = Some("Java, Scala")
        ),
        status = core.generator.Status.Alpha,
        codeGenerator = Some(models.ning.Ning18ClientGenerator)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "play_2_2_client",
          name = "Play 2.2 client",
          description = Some("Play Framework 2.2 client based on <a href='http://www.playframework.com/documentation/2.2.x/ScalaWS''>WS API</a>. Note this client does NOT support HTTP PATCH."),
          language = Some("Scala")
        ),
        status = core.generator.Status.Beta,
        codeGenerator = Some(models.Play22ClientGenerator)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "play_2_3_client",
          name = "Play 2.3 client",
          description = Some("Play Framework 2.3 client based on  <a href='http://www.playframework.com/documentation/2.3.x/ScalaWS'>WS API</a>."),
          language = Some("Scala")
        ),
        status = core.generator.Status.Beta,
        codeGenerator = Some(models.Play23ClientGenerator)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "play_2_x_json",
          name = "Play 2.x json",
          description = Some("Generate play 2.x case classes with json serialization based on <a href='http://www.playframework.com/documentation/2.3.x/ScalaJsonCombinators'>Scala Json combinators</a>. No need to use this target if you are already using the Play Client target."),
          language = Some("Scala")
        ),
        status = core.generator.Status.Beta,
        codeGenerator = Some(models.Play2Models)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "play_2_x_routes",
          name = "Play 2.x routes",
          description = Some("""Generate a routes file for play 2.x framework. See <a href="/doc/playRoutesFile">Play Routes File</a>."""),
          language = Some("Scala")
        ),
        status = core.generator.Status.Beta,
        codeGenerator = Some(models.Play2RouteGenerator)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "scala_models",
          name = "Scala models",
          description = Some("Generate scala models from the API description."),
          language = Some("Scala")
        ),
        status = core.generator.Status.Beta,
        codeGenerator = Some(core.generator.ScalaCaseClasses)
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "swagger_json",
          name = "Swagger JSON",
          description = Some("Generate a valid swagger 2.0 json description of a service."),
          language = None
        ),
        status = core.generator.Status.Proposal,
        codeGenerator = None
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "angular",
          name = "AngularJS client",
          description = Some("Generate a simple to use wrapper to access a service from AngularJS"),
          language = Some("JavaScript")
        ),
        status = core.generator.Status.InDevelopment,
        codeGenerator = None
      ),
      CodeGenTarget(
        metaData = Generator(
          key = "javascript",
          name = "Javascript client",
          description = Some("Generate a simple to use wrapper to access a service from javascript."),
          language = Some("JavaScript")
        ),
        status = core.generator.Status.Proposal,
        codeGenerator = None
      )
  ).sortBy(_.metaData.key)
}
