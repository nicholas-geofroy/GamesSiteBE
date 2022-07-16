package websockets

import akka.actor.ActorRef
import games.GameType.GameType
import play.api.libs.json.{Json, JsValue}

final case class Join(user: String, out: ActorRef)
final case class Leave(user: String)
final case class StartGame()
final case class SetGame(game: GameType)
final case class GetState(user: String)
final case class Ping()
final case class GetUsers()

final case class PlayerActionMsg(actionType: String, data: JsValue)
object PlayerActionMsg {
  implicit val format = Json.format[PlayerActionMsg]
}

final case class PlayerAction(player: String, actionType: String, data: JsValue)
