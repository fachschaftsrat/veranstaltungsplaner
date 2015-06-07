package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.mailer._
import play.modules.authenticator._
import play.modules.reactivemongo._
import javax.inject._
import models._
import scala.concurrent._
import scala.util.{ Try, Success, Failure }
import reactivemongo.bson._
import reactivemongo.core.commands.LastError
import reactivemongo.api.collections.default.BSONCollection

@Singleton
class Application @Inject()(
  implicit val auth: Authenticator,
  mongo: ReactiveMongo,
  mailerClient: MailerClient,
  conf: Configuration
) extends Controller {

  auth.principals.create("admin", "changeme", Map(), Map("admin" -> true, "activated" -> true))

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

  def index = actionWithContext { request ⇒ implicit context ⇒
    Ok(views.html.Index())
  }
  
  def veranstaltungen = asyncActionWithContext { request ⇒ implicit context ⇒
    val futureEvents = mongo.db.collection[BSONCollection]("events")
      .find(BSONDocument())
      .cursor[Event]
      .collect[Seq]()
    futureEvents map { events ⇒
      Ok(views.html.Events(events))
    }
  }

  def register = asyncActionWithContext { request ⇒ implicit context ⇒
    request.method match {
      case "GET" ⇒
        Future.successful(Ok(views.html.Register()))
      case "POST" ⇒
        Registration.validate(request) match {
          case Success(registration) ⇒
            registration.register map {
              case Success(princ) ⇒
                val name = princ.name
                val alink = conf.getString("app.baseURI").get + "/activation/" + name + "/" + princ.field("activationSecret").get
                val email = Email(
                  "Aktivierung deines Accounts",
                  "Fachschaftsrat PAF <fsr@paf.uni-jena.de>",
                  Seq(princ.field("email").get),
                  bodyText = Some(s"""
                    |Hi,
                    |
                    |Du hast den Account $name für den Veranstaltungsplaner des FSR PAF erstellt.
                    |Um deinen Account zu aktivieren, klicke bitte auf folgenden Link:
                    |
                    |$alink
                    |
                    |Grüße,
                    |Dein Fachschaftsrat
                  """.stripMargin)
                )
                mailerClient.send(email)
                success(routes.Application.index(), "Ein Aktivierungslink wurde an deine Mailaddresse versandt.")
                
              case Failure(fail) ⇒
                error(routes.Application.register, fail.getMessage)
            } recover {
              case lastError: LastError ⇒
                if(lastError.code.getOrElse(0) == 11000) error(routes.Application.register(), "Nutzername schon vergeben.")
                else error(routes.Application.register(), "Datenbankfehler: "+lastError.getMessage)
              case t: Throwable ⇒
                error(routes.Application.register(), "Fehler bei der Usererstellung: "+t.getMessage)
            }
          case Failure(fail) ⇒
            Future.successful(error(routes.Application.register, fail.getMessage))
        }
      case _ ⇒
        Future.successful(error(routes.Application.index(), "Falsche HTTP-Methode."))
    }
  }

  def activate(name: String, secret: String) = Action.async {
    auth.principals.find(name) flatMap {
      case Some(princ) ⇒
        if(princ.field("activationSecret").get == secret) {
          princ.flag("activated", true).save map { princ ⇒
            success(routes.Application.index, "Aktivierung erfolgreich")
          }
        } else {
          Future.successful(error(routes.Application.index, "Ungültiger Aktivierungslink"))
        }
      case None ⇒
        Future.successful(error(routes.Application.index, "Nutzer existiert nicht."))
    }
  }

  def login = Action.async { implicit request ⇒
    val body = request.body.asFormUrlEncoded.get
    auth.authenticate(body("username")(0), body("password")(0)) {
      case Some(principal) ⇒
        if(principal.flag("activated").getOrElse(false)) {
          Future.successful(true, Redirect(routes.Application.index()))
        } else {
          Future.successful(false, error(routes.Application.index(), "Dein Account ist noch nicht aktiviert. Bitte schau in deinen SPAM-Ordner."))
        }
      case None ⇒
        Future.successful(false, error(routes.Application.index(), "Falscher Nutzername/Passwort"))
    }
  }

  def logout = Action {
    Redirect(routes.Application.index()).withNewSession
  }

  def profile(name: String) = asyncActionWithContext { implicit request ⇒ implicit context ⇒
    if((context.principal map { princ ⇒ princ.flag("admin").getOrElse(false) || princ.name == name }).getOrElse(false)) {
      auth.principals.find(name) flatMap {
        case Some(princ) ⇒
          request.method match {
            case "GET" ⇒
              Future.successful(Ok(views.html.Profile(Profile(princ))))
            case "POST" ⇒
              val form = request.body.asFormUrlEncoded.get map { case (key, value) ⇒ (key, value(0)) }
              form("section") match {
                case "cpw" ⇒
                  if(form("pw1") == form("pw2") && form("pw1").length > 0) {
                    princ.cpw(form("pw1")).save map { princ ⇒
                      success(routes.Application.profile(name), "Passwort geändert")
                    }
                  } else {
                    Future.successful(error(routes.Application.profile(name), "Passwörter stimmen nicht überein."))
                  }
                case "account" ⇒ 
                  if(context.princIsAdmin) {
                    val admin = form.get("admin").isDefined
                    princ.flag("admin", admin).save map { princ ⇒
                      Redirect(routes.Application.profile(name))
                    }
                  } else {
                    Future.successful(error(routes.Application.profile(name), "Keine Authorisierung"))
                  }
                case _ ⇒
                  Future.successful(error(routes.Application.profile(name), "Ungültige Sektion"))
              }
          }
        case None ⇒
          Future.successful(error(routes.Application.index, "Profil nicht gefunden"))
      }
    } else {
      Future.successful(error(routes.Application.index(), "Du bist nicht authorisiert, dieses Profil zu sehen."))
    }
  }

  def addevent = asyncActionWithContext { request ⇒ implicit context ⇒
    if(context.princIsAdmin) {
      request.method match {
        case "GET" ⇒ Future.successful(Ok(views.html.AddEvent()))
        case "POST" ⇒
          val event = Event.validate(request).get
          mongo.db.collection[BSONCollection]("events").insert(event) map { lastError ⇒
            if(lastError.ok) {
              success(routes.Application.index, "Event hinzugefügt")
            } else {
              throw lastError
            }
          }
      }
    } else {
      Future.successful(error(routes.Application.index, "Keine Authorisierung"))
    }
  }

  def showevent(name: String) = asyncActionWithContext { request ⇒ implicit context ⇒
    val futureEvent = mongo.db.collection[BSONCollection]("events")
      .find(BSONDocument("name" -> name))
      .cursor[Event]
      .collect[Seq]() map {
        _(0)
    }
    futureEvent flatMap { event ⇒
      val futureIsPart: Future[Boolean] = context.principal match {
        case Some(princ) ⇒ event.isParticipant(princ)
        case None ⇒ Future.successful(false)
      }
      futureIsPart flatMap { isPart ⇒
        event.participants map { parts ⇒
          Ok(views.html.ShowEvent(event, isPart, parts))
        }
      }
    }
  }

  def anmelden(name: String) = asyncActionWithContext { request ⇒ implicit context ⇒
    context.principal match {
      case Some(princ) ⇒
        val futureEvent = mongo.db.collection[BSONCollection]("events")
          .find(BSONDocument("name" -> name))
          .cursor[Event]
          .collect[Seq]() map {
            _(0)
        } 

        futureEvent flatMap { event ⇒
          event.addParticipant(princ) map { _ ⇒
            success(routes.Application.showevent(name), "Du wurdest erfolgreich angemeldet.")
          }
        }
      case None ⇒
        Future.successful(error(routes.Application.showevent(name), "Dafür musst du angemeldet sein."))
    }
  }
}
