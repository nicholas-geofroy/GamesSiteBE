package models

import play.api.libs.json._
import games.GameType.GameType

object lobbymodels {
  case class LobbyInMsg(msgType: String, data: JsValue)
  object LobbyInMsg {
      implicit val inMsgFormat = Json.format[LobbyInMsg]
  }
  case class LobbyOutMsg(userId: String, msgType: String, data: JsObject)
  object LobbyOutMsg {
      implicit val outMsgFormat = Json.format[LobbyOutMsg]
  }

  sealed trait LobbyMsg {
    def getType(): String
    def getFormat(): Format[LobbyMsg]
  }
  
  case class EmptyMsg() extends LobbyMsg {
    def getType(): String = "StartGame"

    private val reads = Reads.pure(EmptyMsg())
    private val writes = OWrites[EmptyMsg](_ => Json.obj())
    def getFormat(): Format[LobbyMsg] = Format(reads, writes).asInstanceOf[Format[LobbyMsg]]
  }

  final object StartGameMsg {
    def apply() = new EmptyMsg()
  }

  final case class LobbyUsersMsg(id: String, users: List[String]) extends LobbyMsg {
    def getType(): String = "Members"
    def getFormat(): Format[LobbyMsg] = Json.format[LobbyUsersMsg].asInstanceOf[Format[LobbyMsg]]
  }

  final case class LobbyGameTypeMsg(gameType: GameType) extends LobbyMsg {
    def getType(): String = "GameType"
    def getFormat(): Format[LobbyMsg] = Json.format[LobbyGameTypeMsg].asInstanceOf[Format[LobbyMsg]]
  }

  final case class LobbyStateMsg(state: JsValue) extends LobbyMsg {
    def getType(): String = "GameState"
    def getFormat(): Format[LobbyMsg] = Json.format[LobbyStateMsg].asInstanceOf[Format[LobbyMsg]]
  }


  object ErrorType {
    val NotEnoughPlayers = "NotEnoughPlayers"
    val LobbyClosed = "LobbyClosed"
    val unknown = "Unknown"
  }

  final case class ErrorMsg(errorType: String, message: String) extends LobbyMsg {
    def getType(): String = "Error"
    def getFormat(): Format[LobbyMsg] = Json.format[ErrorMsg].asInstanceOf[Format[LobbyMsg]]
  }
  
}
