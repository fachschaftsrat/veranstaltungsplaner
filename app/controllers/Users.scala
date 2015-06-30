package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.mailer._
import play.modules.authenticator._
import play.modules.reactivemongo._
import scala.concurrent._
import scala.util.{ Try, Success, Failure }
import reactivemongo.bson._
import reactivemongo.core.commands.LastError
import reactivemongo.api.collections.default.BSONCollection
import javax.inject._
import models._

@Singleton
class Users @Inject()(implicit
  auth: Authenticator,
  mongo: ReactiveMongo,
  mailerClient: MailerClient,
  conf: Configuration
) extends ExtendedController {

  auth.principals.create("admin", "changeme", BSONDocument("admin" -> true, "activated" -> true))

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
                val alink = conf.getString("app.baseURI").get + "/activation/" + princ.id + "/" + princ.value[String]("activationSecret").get
                val email = Email(
                  "Aktivierung deines Accounts",
                  "Fachschaftsrat PAF <fsr@paf.uni-jena.de>",
                  Seq(princ.value[String]("email").get),
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
                error(routes.Users.register, fail.getMessage)
            } recover {
              case lastError: LastError ⇒
                if(lastError.code.getOrElse(0) == 11000) error(routes.Users.register(), "Nutzername schon vergeben.")
                else error(routes.Users.register(), "Datenbankfehler: "+lastError.getMessage)
              case t: Throwable ⇒
                error(routes.Users.register(), "Fehler bei der Usererstellung: "+t.getMessage)
            }
          case Failure(fail) ⇒
            Future.successful(error(routes.Users.register, fail.getMessage))
        }
      case _ ⇒
        Future.successful(error(routes.Application.index(), "Falsche HTTP-Methode."))
    }
  }

  def activate(id: String, secret: String) = Action.async {
    auth.principals.findByID(id) flatMap {
      case Some(princ) ⇒
        if(princ.value[String]("activationSecret").get == secret) {
          princ.value("activated", true).save map { princ ⇒
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
    auth.authenticateWithPassword(body("username")(0), body("password")(0)) {
      case Some(principal) ⇒
        if(principal.value[Boolean]("activated").getOrElse(false)) {
          Future.successful(true, Redirect(routes.Events.events()))
        } else {
          Future.successful(false, error(routes.Application.index(), "Dein Account ist noch nicht aktiviert. Bitte schau in deinen SPAM-Ordner."))
        }
      case None ⇒
        Future.successful(false, error(routes.Application.index(), "Falscher Nutzername/Passwort"))
    }
  }

  def openidlogin = Action.async { implicit request ⇒
    val body = request.body.asFormUrlEncoded.get
    auth.authenticateWithOpenID(body("openid")(0), routes.Users.openidcallback)
  }

  def openidcallback = Action.async { implicit request ⇒
    auth.openIDCallback {
      case (Some(princ), _, _) ⇒
        if(princ.value[Boolean]("activated").getOrElse(false)) Future.successful((true, Redirect(routes.Events.events())))
        else Future.successful(false, error(routes.Application.index(), "Dein Account ist noch nicht aktiviert. Bitte schau in deinen SPAM-Ordner."))
      case (None, Some(openid), _) ⇒ Future.successful((false, Redirect(routes.Users.register)))
      case (None, None, _) ⇒ Future.successful((false, error(routes.Application.index, "Login fehlgeschlagen")))
    }
  }

  def logout = Action.async { implicit request ⇒
    auth.unauthenticate(Redirect(routes.Application.index()))
  }

  def profile(id: String) = asyncActionWithContext { implicit request ⇒ implicit context ⇒
    if((context.principal map { princ ⇒ princ.value[Boolean]("admin").getOrElse(false) || princ.id == id }).getOrElse(false)) {
      auth.principals.findByID(id) flatMap {
        case Some(princ) ⇒
          request.method match {
            case "GET" ⇒
              Future.successful(Ok(views.html.Profile(Profile(princ))))
            case "POST" ⇒
              val form = request.body.asFormUrlEncoded.get map { case (key, value) ⇒ (key, value(0)) }
              form("section") match {
                case "cpw" ⇒
                  if(form("pw1") == form("pw2") && form("pw1").length > 0) {
                    princ.changePassword(form("pw1")).save map { princ ⇒
                      success(routes.Users.profile(id), "Passwort geändert")
                    }
                  } else {
                    Future.successful(error(routes.Users.profile(id), "Passwörter stimmen nicht überein."))
                  }
                case "account" ⇒ 
                  if(context.princIsAdmin) {
                    val admin = form.get("admin").isDefined
                    princ.value("admin", admin).save map { princ ⇒
                      Redirect(routes.Users.profile(id))
                    }
                  } else {
                    Future.successful(error(routes.Users.profile(id), "Keine Authorisierung"))
                  }
                case _ ⇒
                  Future.successful(error(routes.Users.profile(id), "Ungültige Sektion"))
              }
          }
        case None ⇒
          Future.successful(error(routes.Application.index, "Profil nicht gefunden"))
      }
    } else {
      Future.successful(error(routes.Application.index(), "Du bist nicht authorisiert, dieses Profil zu sehen."))
    }
  }

  def allusers = asyncActionWithContext { implicit request ⇒ implicit context ⇒
    if(context.princIsAdmin) {
      auth.principals.findAll map { seq ⇒
        Ok(views.html.AllUsers(seq map { Profile(_) }))
      }
    } else {
      Future.successful(error(routes.Application.index, "Nicht authorisiert."))
    }
  }
}
