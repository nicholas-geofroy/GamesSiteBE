package models

import java.util.UUID
import play.api.libs.json.Json

object gamemodels {
  case class Lobby(
      id: String = UUID.randomUUID().toString(),
      members: List[String] = List.empty
  )
  object Lobby {
    implicit val format = Json.format[Lobby]
  }
}
