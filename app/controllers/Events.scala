package controllers

import play.api._
import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._
import play.modules.authenticator._
import play.modules.reactivemongo._
import scala.concurrent._
import scala.util.{ Try, Success, Failure }
import javax.inject._
import models._

@Singleton
class Events @Inject()(implicit
  auth: AuthenticatorApi,
  principals: PrincipalsApi,
  mongo: ReactiveMongoApi
) extends ExtendedController {
  
  def list = asyncActionWithContext { request ⇒ implicit context ⇒
    Event.findAll map { events ⇒
      Ok(views.html.Events(events))
    }
  }

  def add = asyncActionWithContext { request ⇒ implicit context ⇒
    if(context.princIsAdmin) {
      request.method match {
        case "GET" ⇒ Future.successful(Ok(views.html.AddEvent()))
        case "POST" ⇒
          val event = Event.validate(request).get
          event.insert() map {
            case Success(_) ⇒ success(routes.Application.index, "Event hinzugefügt")
            case Failure(f) ⇒ error(routes.Events.add, "Fehler: " + f.getMessage)
          }
      }
    } else {
      Future.successful(error(routes.Application.index, "Keine Authorisierung"))
    }
  }
  
  def edit(eventId: String) = asyncActionWithContext { request ⇒ implicit context ⇒
    if(context.princIsAdmin) {
      Event.find(eventId) flatMap { eventOption ⇒
        val event = eventOption.get
        request.method match {
          case "GET" ⇒ Future.successful(Ok(views.html.EditEvent(event)))
          case "POST" ⇒
            val formEvent = Event.validate(request).get
            event.copy(name = formEvent.name, ort = formEvent.ort, zeit = formEvent.zeit, beschreibung = formEvent.beschreibung, signOffEnabled = formEvent.signOffEnabled).save map {
              case Success(event) ⇒ success(routes.Events.show(event.id), "Gespeichert")
              case Failure(f) ⇒ success(routes.Events.show(event.id), f.getMessage)
            }
        }
      }
    } else {
      Future.successful(error(routes.Application.index, "Keine Authorisierung"))
    }
  }

  def show(id: String) = asyncActionWithContext { request ⇒ implicit context ⇒
    Event.find(id) flatMap { event ⇒
      val isPart: Boolean = context.principal match {
        case Some(princ) ⇒ event.get.isParticipant(princ)
        case None ⇒ false
      }
      event.get.participants() map { parts ⇒
        Ok(views.html.ShowEvent(event.get, isPart, parts))
      }
    }
  }

  def signup(id: String) = asyncActionWithContext { request ⇒ implicit context ⇒
    context.principal match {
      case Some(princ) ⇒
        Event.find(id) flatMap { event ⇒
          event.get.addParticipant(princ).save map { _ ⇒
            success(routes.Events.show(id), "Du wurdest erfolgreich angemeldet.")
          }
        }
      case None ⇒
        Future.successful(error(routes.Events.show(id), "Dafür musst du angemeldet sein."))
    }
  }

  def signoff(id: String) = asyncActionWithContext { request ⇒ implicit context ⇒
    context.principal match {
      case Some(princ) ⇒
        Event.find(id) flatMap { event ⇒
          if(event.get.signOffEnabled) {
            event.get.removeParticipant(princ).save() map { _ ⇒
              success(routes.Events.show(id), "Du wurdest erfolgreich abgemeldet.")
            }
          } else {
            throw new Exception("Sign off disabled")
          }
        }
      case None ⇒
        Future.successful(error(routes.Events.show(id), "Dafür musst du angemeldet sein."))
    }
  }

  def toggleOpen(id: String) = asyncActionWithContext { request ⇒ implicit context ⇒
    if(!context.princIsAdmin) throw new Exception("Keine Authorisierung")
    Event.find(id) map { _.get } flatMap { event ⇒
      event.setOpen(!event.open).save map {
        case Success(_) ⇒ Redirect(routes.Events.show(id))
        case Failure(f) ⇒ error(routes.Events.show(id), f.getMessage)
      }
    } 
  }

  def removeParticipant(eventId: String, userId: String) = asyncActionWithContext { request ⇒ implicit context ⇒
    if(!context.princIsAdmin) throw new Exception("Keine Authorisierung")
    Event.find(eventId) map { _.get } flatMap { event ⇒
      principals.findByID(userId) flatMap { principal ⇒
        event.removeParticipant(principal.get).save map {
          case Success(event) ⇒ success(routes.Events.show(event.id), "Teilnehmer entfernt")
          case Failure(f) ⇒ error(routes.Events.show(event.id), f.getMessage)
        }
      }
    }
  }
}
