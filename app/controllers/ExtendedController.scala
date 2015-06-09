package controllers

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import play.modules.authenticator._
import play.modules.reactivemongo._
import scala.concurrent._
import models._

abstract class ExtendedController(implicit
  auth: Authenticator,
  mongo: ReactiveMongo
) extends Controller {

  def urlencode(str: String): String = {
      java.net.URLEncoder.encode(str, "UTF-8");
  }

  def actionWithContext(action: (Request[AnyContent]) ⇒ (RequestContext) ⇒ (Result)): Action[AnyContent] = {
    Action.async { implicit request ⇒
      RequestContext() map { context ⇒
        action(request)(context)
      }
    }
  }

  def asyncActionWithContext(action: (Request[AnyContent]) ⇒ (RequestContext) ⇒ (Future[Result])): Action[AnyContent] = {
    Action.async { implicit request ⇒
      RequestContext() flatMap { context ⇒
        action(request)(context)
      }
    }
  }

  def error(call: Call, message: String): Result = {
    Redirect(call).flashing("error" -> message)
  }

  def success(call: Call, message: String): Result = {
    Redirect(call).flashing("success" -> message)
  }

  def notice(call: Call, message: String): Result = {
    Redirect(call).flashing("notice" -> message)
  }
}
