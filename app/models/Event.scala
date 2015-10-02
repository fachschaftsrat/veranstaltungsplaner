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
  beschreibung: String,
  // Whether registration to this event is open
  open: Boolean,
  // List of principal ids participating
  participantIDs: Seq[String],
  // Whether a participant may sign off without
  signOffEnabled: Boolean
) {

  def participants()(implicit auth: Authenticator, ec: ExecutionContext): Future[Seq[Principal]] = {
    Future.sequence(participantIDs.map({ id ⇒ auth.principals.findByID(id) })) map { seq ⇒
      seq.filter({ _.isDefined}).map({ _.get })
    }
  }

  def isParticipant(princ: Principal): Boolean = participantIDs.filter({ _ == princ.id }).size > 0

  def addParticipant(princ: Principal): Event = {
    if(!isParticipant(princ)) copy(participantIDs = participantIDs :+ princ.id)
    else this
  }

  def removeParticipant(princ: Principal) = {
    copy(participantIDs = participantIDs.filter({ _ != princ.id }))
  }

  def insert()(implicit mongo: ReactiveMongo, ec: ExecutionContext): Future[Try[Event]] = {
    mongo.db.collection[BSONCollection]("events").insert(this)(Event.bsonWriter, ec) map { lastError ⇒
      if(lastError.ok) {
        Success(this)
      } else {
        Failure(lastError)
      }
    }
  }

  def setOpen(open: Boolean): Event = {
    copy(open = open)
  }

  def save()(implicit mongo: ReactiveMongo, ec: ExecutionContext): Future[Try[Event]] = {
    mongo.db.collection[BSONCollection]("events").update(BSONDocument("_id" -> BSONObjectID(id)), this) map { lastError ⇒
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
        form("beschreibung"),
        false,
        Seq(),
        if(form("signoff") == "true") true else false
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
        bson.getAs[String]("beschreibung").get,
        bson.getAs[Boolean]("open").getOrElse(false),
        bson.getAs[Seq[String]]("participantIDs").get,
        bson.getAs[Boolean]("signOffEnabled").getOrElse(true)
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
        "beschreibung" -> event.beschreibung,
        "open" -> event.open,
        "participantIDs" -> event.participantIDs,
        "signOffEnabled" -> event.signOffEnabled
      )
    }
  }
}
