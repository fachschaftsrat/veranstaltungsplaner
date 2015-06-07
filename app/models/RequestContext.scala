package models

import play.modules.authenticator.{ Authenticator, Principal }
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import scala.concurrent._

case class RequestContext(
  principal: Option[Principal],
  error: Option[String],
  success: Option[String],
  notice: Option[String],
  princIsAdmin: Boolean
)

object RequestContext {

  def apply()(implicit authenticator: Authenticator, request: Request[AnyContent]): Future[RequestContext] = {
    authenticator.principal map { principal ⇒
      val flash = request.flash
      RequestContext(
        principal,
        flash.get("error"),
        flash.get("success"),
        flash.get("notice"),
        principal.isDefined && principal.get.flag("admin").getOrElse(false)
      )
    }
  }
}
