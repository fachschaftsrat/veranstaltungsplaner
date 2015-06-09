package controllers

import play.api.mvc._
import play.modules.authenticator._
import play.modules.reactivemongo._
import javax.inject._

@Singleton
class Application @Inject()(implicit
  auth: Authenticator,
  mongo: ReactiveMongo
) extends ExtendedController {

  def index = actionWithContext { request ⇒ implicit context ⇒
    Ok(views.html.Index())
  }
}
