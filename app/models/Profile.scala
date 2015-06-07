package models

import play.modules.authenticator._

case class Profile(
  principal: Principal,
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
      principal.name,
      principal.field("email").getOrElse(""),
      principal.field("vorname").getOrElse(""),
      principal.field("nachname").getOrElse(""),
      principal.field("studiengang").getOrElse(""),
      principal.field("tel").getOrElse(""),
      principal.flag("admin").getOrElse(false)
    )
  }
}
