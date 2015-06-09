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
  id: String,
  name: String,
  zeit: String,
  ort: String,
  beschreibung: String
) {

  def participants()(implicit mongo: ReactiveMongo, auth: Authenticator, ec: ExecutionContext): Future[Seq[Principal]] = {
    val fParts: Future[Seq[Participation]] = mongo.db.collection[BSONCollection]("participations")
      .find(BSONDocument("eventID" -> id))
      .cursor[Participation]
      .collect[Seq]()

    val fIDs: Future[Seq[String]] = fParts map { seq ⇒
      seq map { _.princID }
    }

    val ffPrincOptions: Future[Seq[Future[Option[Principal]]]] = fIDs map { seq ⇒
      seq map { id ⇒ auth.principals.findByID(id) }
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
        mongo.db.collection[BSONCollection]("participations").insert(Participation(principal.id, id)) map { _ ⇒ Unit }
      }
    }
  }

  def save()(implicit mongo: ReactiveMongo, ec: ExecutionContext): Future[Try[Event]] = {
    mongo.db.collection[BSONCollection]("events").insert(this)(Event.bsonWriter, ec) map { lastError ⇒
      if(lastError.ok) {
        Success(this)
      } else {
        Failure(lastError)
      }
    }
  }
}

object Event {

  def validate(request: Request[AnyContent]): Try[Event] = {
    val form = request.body.asFormUrlEncoded.get map { case (key, value) ⇒ (key, value(0)) }
    try {
      Success(Event(
        BSONObjectID.generate.stringify,
        form("name"),
        form("zeit"),
        form("ort"),
        form("beschreibung")
      ))
    } catch {
      case t: Throwable ⇒ Failure(t)
    }
  }

  def findAll()(implicit mongo: ReactiveMongo, ec: ExecutionContext): Future[Seq[Event]] = {
    mongo.db.collection[BSONCollection]("events")
      .find(BSONDocument())
      .cursor[Event]
      .collect[Seq]()
  }

  def find(id: String)(implicit mongo: ReactiveMongo, ec: ExecutionContext): Future[Option[Event]] = {
    mongo.db.collection[BSONCollection]("events")
      .find(BSONDocument("_id" -> BSONObjectID(id)))
      .cursor[Event]
      .collect[Seq]() map { seq ⇒
        if(seq.length > 0) Some(seq(0))
        else None
    }
  }

  implicit val bsonReader = new BSONDocumentReader[Event] {
    def read(bson: BSONDocument): Event = {
      Event(
        bson.getAs[BSONObjectID]("_id").get.stringify,
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
        "_id" -> BSONObjectID(event.id),
        "name" -> event.name,
        "zeit" -> event.zeit,
        "ort" -> event.ort,
        "beschreibung" -> event.beschreibung
      )
    }
  }
}
