package models

import reactivemongo.bson._
import reactivemongo.api._

case class Participation(
  princName: String,
  eventName: String
)

object Participation {

  implicit val bsonReader = new BSONDocumentReader[Participation] {
    def read(bson: BSONDocument): Participation = {
      Participation(
        bson.getAs[String]("princName").get,
        bson.getAs[String]("eventName").get
      )
    }
  }

  implicit val bsonWriter = new BSONDocumentWriter[Participation] {
    def write(part: Participation): BSONDocument = {
      BSONDocument(
        "princName" -> part.princName,
        "eventName" -> part.eventName
      )
    }
  }
}
