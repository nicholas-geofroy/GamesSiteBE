package games
import scala.util.Try
import play.api.libs.json.JsValue
import websockets.PlayerAction

trait Move {}

trait GameState {
  def toJsonWithFilter(player: String): JsValue
}

trait Game {
  def tryInit(players: List[String]): Try[Unit]
  def receiveAction(fromPlayer: String, action: Move): Try[Unit]
  def getState(): GameState
  def parseAction(msg: PlayerAction): Try[Move]
}

object GameType extends Enumeration {
  type GameType = String
  val ohHell = "OhHell"
  val justOne = "JustOne"
}

class InvalidMsgException extends Throwable
class InvalidAction extends Throwable
class NotEnoughPlayers extends Throwable