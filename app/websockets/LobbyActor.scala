package websockets

import akka.actor._
import akka.actor.typed.scaladsl
import scala.collection.mutable.Map
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import models.lobbymodels._
import play.api.libs.json._
import auth.AuthService
import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.ReceiveTimeout
import games.OhHell
import games.GameState
import games.Card
import scala.util.Failure
import scala.util.Try
import scala.util.Success
import games.LobbyState
import games.GameType.GameType
import games.Game
import games.JustOne

final case class Join(user: String, out: ActorRef)
final case class Leave(user: String)
final case class StartGame()
final case class SetGame(game: GameType)
final case class Ping()

final case class PlayerActionMsg(actionType: String, data: JsValue)
object PlayerActionMsg {
  implicit val format = Json.format[PlayerActionMsg]
}

final case class PlayerAction(player: String, actionType: String, data: JsValue)

object LobbyActor {
  def props(out: ActorRef, manager: ActorRef, authService: AuthService) = Props(
    new LobbyActor(out, manager, authService)
  )
}

class LobbyActor(out: ActorRef, manager: ActorRef, authService: AuthService)
    extends Actor {
  import context._

  //if we dont hear the initial message, then error out
  setReceiveTimeout(5 seconds)
  var userId = ""

  override def postStop(): Unit = {
    if (!userId.isEmpty()) {
      println(f"Post stop for ${userId}")
      manager ! Leave(userId)
    }
  }

  def receive = {
    case LobbyInMsg(msgType, data) =>
      println("Received a message")
      msgType match {
        case "Join" => {
          (data \ "Authorization").asOpt[String] match {
            case Some(token) => {
              authService.validateToken(token) match {
                case Right(jwtClaim) =>
                  jwtClaim.subject match {
                    case Some(id) => {
                      userId = id
                      //authenticated JWT Token path
                      context.setReceiveTimeout(Duration.Undefined)
                      manager ! Join(userId, self)
                      become(lobby)
                    }
                    case None => sendErrorMessage("No subject on auth token")
                  }
                case Left(result) => sendErrorMessage("Invalid Bearer Token")
              }
            }
            case None => {
              self ! PoisonPill
            }
          }
        }
      }
    case ReceiveTimeout =>
      println("Close websocket due to auth timeout")
      self ! PoisonPill
  }

  def baseReceive: Receive = {
    case LobbyInMsg(msgType, _) if msgType == "Ping" =>
      manager ! Ping()
  }

  def lobby: Receive = baseReceive orElse {
    // if we receive a message from the manager simply convert to json and send to the user
    case msg: LobbyMsg =>
      out ! LobbyOutMsg(
        userId,
        msg.getType(),
        msg.getFormat().writes(msg).asInstanceOf[JsObject]
      )

    // if we receive a message from the socket, convert to scala type and send to manager
    case LobbyInMsg(msgType, data) =>
      msgType match {
        case "Ping" => {} //ignore the ping messages, used for keep alive
        case "Start" =>
          println("Start game message received")
          manager ! StartGame()
        case "GameAction" =>
          data.validate[PlayerActionMsg].asOpt match {
            case Some(PlayerActionMsg(aType, data)) => manager ! PlayerAction(userId, aType, data)
            case None       => sendErrorMessage("Invalid data for GameAction Command")
          }
        case _ =>
          println("Unknown Message Sent from client")
      }
  }

  def sendErrorMessage(message: String) = {
    out ! LobbyOutMsg(userId, "error", Json.obj("message" -> message))
  }
}

object LobbyManager {
  def props(lobbyId: String) = Props(new LobbyManager(lobbyId))
}

class LobbyManager(val id: String) extends Actor {
  import context._

  val users: Map[String, ActorRef] = Map.empty
  var gameType: GameType = games.GameType.justOne
  var game: Game = null

  // if we don't receive mesages after 30s, timeout
  context.setReceiveTimeout(30 seconds)

  def baseReceive: Receive = {
    case Ping => {} //ignore pings
  }

  def receive: Receive = baseReceive orElse {
    case Join(user, out) =>
      println(f"User ${user} joined lobby ${id}")
      users.put(user, out)
      users.foreach(_._2 ! LobbyUsersMsg(id, users.map(_._1).toList))
      sendAll(LobbyGameTypeMsg(gameType))
    case Leave(user) =>
      println(f"User ${user} left lobby ${id}")
      users.remove(user)
      users.foreach(_._2 ! LobbyUsersMsg(id, users.map(_._1).toList))
    case SetGame(newGameType) =>
      println(f"Set Game to type: ${newGameType}")
      gameType = newGameType
      sendAll(LobbyGameTypeMsg(newGameType))
    case msg: StartGame =>
      println("Start Game!")
      val players = users.keySet.toList
      game = gameType match {
        case games.GameType.ohHell => new OhHell()
        case games.GameType.justOne => new JustOne()
      }
      
      val result = game.tryInit(players)
      if (result.isFailure) {
        sendAll(
          ErrorMsg(
            ErrorType.unknown,
            "Unable to start the lobby"
          )
        )
      } else {
        sendAll(StartGameMsg())
        sendState()
        become(gameReceive)
      }
    case ReceiveTimeout =>
      if (users.isEmpty) {
        println(
          "Lobby created but no users joined in 10s interval of no messages, killing"
        )
        stop(self)
      }
  }

  def gameReceive: Receive = baseReceive orElse {
    case Join(user, out) =>
      println(f"User ${user} attempted to join lobby ${id} during the game")
      if (users.contains(user)) {
        println("Admitted because the user was already in the lobby")
        users.put(user, out)
        sendState()
      } else {
        println("Lobby already has started, can't join")
        sender() ! ErrorMsg(
          ErrorType.LobbyClosed,
          "Game Has already started, can't join the lobby"
        )
      }
    case Leave(user) =>
      println(f"User ${user} left the lobby during the game")
    case action: PlayerAction =>
      game.parseAction(action)
  }

  def sendFailureIfFail(user: String, attempt: Try[_]) = {
    attempt match {
      case Failure(exception) => sendError(user, exception.getMessage())
      case Success(value)     => sendState()
    }
  }

  def sendAll(message: LobbyMsg) = users.foreach(_._2 ! message)

  def sendError(user: String, message: String) = {
    users(user) ! ErrorMsg("unknown", message)
  }

  def sendState() = {
    val state = game.getState()
    println("Sending state")
    users.foreach(tuple =>
      tuple._2 ! LobbyStateMsg(state.toJsonWithFilter(tuple._1))
    )
  }
}
