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
  auth: Authenticator,
  mongo: ReactiveMongo
) extends ExtendedController {
  
  def events = asyncActionWithContext { request ⇒ implicit context ⇒
    Event.findAll map { events ⇒
      Ok(views.html.Events(events))
    }
  }

  def addevent = asyncActionWithContext { request ⇒ implicit context ⇒
    if(context.princIsAdmin) {
      request.method match {
        case "GET" ⇒ Future.successful(Ok(views.html.AddEvent()))
        case "POST" ⇒
          val event = Event.validate(request).get
          event.insert() map {
            case Success(_) ⇒ success(routes.Application.index, "Event hinzugefügt")
            case Failure(f) ⇒ error(routes.Events.addevent, "Fehler: " + f.getMessage)
          }
      }
    } else {
      Future.successful(error(routes.Application.index, "Keine Authorisierung"))
    }
  }

  def showevent(id: String) = asyncActionWithContext { request ⇒ implicit context ⇒
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
            success(routes.Events.showevent(id), "Du wurdest erfolgreich angemeldet.")
          }
        }
      case None ⇒
        Future.successful(error(routes.Events.showevent(id), "Dafür musst du angemeldet sein."))
    }
  }

  def signoff(id: String) = asyncActionWithContext { request ⇒ implicit context ⇒
    context.principal match {
      case Some(princ) ⇒
        Event.find(id) flatMap { event ⇒
          event.get.removeParticipant(princ).save() map { _ ⇒
            success(routes.Events.showevent(id), "Du wurdest erfolgreich abgemeldet.")
          }
        }
      case None ⇒
        Future.successful(error(routes.Events.showevent(id), "Dafür musst du angemeldet sein."))
    }
  }

  def toggleOpen(id: String) = asyncActionWithContext { request ⇒ implicit context ⇒
    if(!context.princIsAdmin) throw new Exception("Keine Authorisierung")
    Event.find(id) map { _.get } flatMap { event ⇒
      event.setOpen(!event.open).save map {
        case Success(_) ⇒ Redirect(routes.Events.showevent(id))
        case Failure(f) ⇒ error(routes.Events.showevent(id), f.getMessage)
      }
    } 
  }
}
