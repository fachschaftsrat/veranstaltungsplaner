package models

import play.modules.authenticator._

case class Profile(
  principal: Principal,
  id: String,
  name: String,
  email: String,
  vorname: String,
  nachname: String,
  studiengang: String,
  tel: String,
  admin: Boolean
)

object Profile {

  def apply(principal: Principal): Profile = {
    Profile(
      principal,
      principal.id,
      principal.name,
      principal.value[String]("email").getOrElse(""),
      principal.value[String]("vorname").getOrElse(""),
      principal.value[String]("nachname").getOrElse(""),
      principal.value[String]("studiengang").getOrElse(""),
      principal.value[String]("tel").getOrElse(""),
      principal.value[Boolean]("admin").getOrElse(false)
    )
  }
}
