package models

import reactivemongo.bson._
import reactivemongo.api._
import reactivemongo.api.collections.default._
import play.modules.authenticator._
import play.modules.reactivemongo._
import play.api.mvc._
import scala.util.{ Try, Success, Failure }
import scala.concurrent._

case class Event(
  name: String,
  zeit: String,
  ort: String,
  beschreibung: String
) {

  def participants()(implicit mongo: ReactiveMongo, auth: Authenticator, ec: ExecutionContext): Future[Seq[Principal]] = {
    val fParts: Future[Seq[Participation]] = mongo.db.collection[BSONCollection]("participations")
      .find(BSONDocument("eventName" -> name))
      .cursor[Participation]
      .collect[Seq]()

    val fNames: Future[Seq[String]] = fParts map { seq ⇒
      seq map { _.princName }
    }

    val ffPrincOptions: Future[Seq[Future[Option[Principal]]]] = fNames map { seq ⇒
      seq map { name ⇒ auth.principals.find(name) }
    }

    val fPrincOptions: Future[Seq[Option[Principal]]] = ffPrincOptions flatMap { seq ⇒
      Future.sequence(seq)
    }

    fPrincOptions map { seq ⇒
      seq.filter { _.isDefined } map { _.get }
    }
  }

  def isParticipant(princ: Principal)(implicit mongo: ReactiveMongo, auth: Authenticator, ec: ExecutionContext): Future[Boolean] = {
    participants map { seq ⇒ seq map { _.name } } map { names ⇒ names.contains(princ.name) }
  }

  def addParticipant(principal: Principal)(implicit mongo: ReactiveMongo, auth: Authenticator, ec: ExecutionContext): Future[Unit] = {
    isParticipant(principal) flatMap { isPart ⇒
      if(isPart) Future.successful(Unit)
      else {
        mongo.db.collection[BSONCollection]("participations").insert(Participation(principal.name, name)) map { _ ⇒ Unit }
      }
    }
  }
}

object Event {

  def validate(request: Request[AnyContent]): Try[Event] = {
    val form = request.body.asFormUrlEncoded.get map { case (key, value) ⇒ (key, value(0)) }
    try {
      Success(Event(
        form("name"),
        form("zeit"),
        form("ort"),
        form("beschreibung")
      ))
    } catch {
      case t: Throwable ⇒ Failure(t)
    }
  }

  implicit val bsonReader = new BSONDocumentReader[Event] {
    def read(bson: BSONDocument): Event = {
      Event(
        bson.getAs[String]("name").get,
        bson.getAs[String]("zeit").get,
        bson.getAs[String]("ort").get,
        bson.getAs[String]("beschreibung").get
      )
    }
  }

  implicit val bsonWriter = new BSONDocumentWriter[Event] {
    def write(event: Event): BSONDocument = {
      BSONDocument(
        "name" -> event.name,
        "zeit" -> event.zeit,
        "ort" -> event.ort,
        "beschreibung" -> event.beschreibung
      )
    }
  }
}
