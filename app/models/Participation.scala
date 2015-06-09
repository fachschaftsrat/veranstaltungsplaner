package models

import reactivemongo.bson._
import reactivemongo.api._

case class Participation(
  princID: String,
  eventID: String
)

object Participation {

  implicit val bsonReader = new BSONDocumentReader[Participation] {
    def read(bson: BSONDocument): Participation = {
      Participation(
        bson.getAs[String]("princID").get,
        bson.getAs[String]("eventID").get
      )
    }
  }

  implicit val bsonWriter = new BSONDocumentWriter[Participation] {
    def write(part: Participation): BSONDocument = {
      BSONDocument(
        "princID" -> part.princID,
        "eventID" -> part.eventID
      )
    }
  }
}
