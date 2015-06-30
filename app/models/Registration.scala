package models

import play.api.mvc._
import play.modules.authenticator._
import scala.util.{ Try, Success, Failure }
import scala.concurrent._
import xyz.wiedenhoeft.scalacrypt._
import reactivemongo.bson._

case class Registration(
  nutzername: String,
  email: String,
  vorname: String,
  nachname: String,
  studiengang: String,
  tel: String,
  password: String
) {

  def register()(implicit auth: Authenticator, ec: ExecutionContext): Future[Try[Principal]] = {
    def bytesToHex(bytes: Seq[Byte]): String = {
      val hexArray = "0123456789abcdef".toCharArray
      val hexChars = new Array[Char](bytes.length * 2)
      for(i <- 0 until bytes.length) {
        val v = bytes(i) & 0xFF
        hexChars(i * 2) = hexArray(v >>> 4)
        hexChars(i * 2 + 1) = hexArray(v & 0x0F)
      }
      return new String(hexChars);
    }

    auth.principals.createWithPassword(
      nutzername,
      password,
      BSONDocument(
        "email" -> email,
        "vorname" -> vorname,
        "nachname" -> nachname,
        "studiengang" -> studiengang,
        "tel" -> tel,
        "activationSecret" -> bytesToHex(Random.nextBytes(16)),
        "activated" -> false,
        "admin" -> false
      )
    )
  }
}

object Registration {
  
  def validate(request: Request[AnyContent]): Try[Registration] = {
    val form = request.body.asFormUrlEncoded.get
    try {
      if(form("pw1")(0) == form("pw2")(0) && form("pw1")(0).length > 0) {
          Success(Registration(
            form("nutzername")(0),
            form("email")(0),
            form("vorname")(0),
            form("nachname")(0),
            form("studiengang")(0),
            form.get("tel").getOrElse(Seq(""))(0),
            form("pw1")(0)
          ))
      } else {
        Failure(new Exception("Passwörter stimmen nicht überein"))
      }
    } catch {
      case t: Throwable ⇒ Failure(t)
    }
  }
}
