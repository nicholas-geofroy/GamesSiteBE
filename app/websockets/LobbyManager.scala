package websockets
import play.api.Environment
import akka.actor._
import games.GameType.GameType
import games.Game
import scala.concurrent.duration._
import models.lobbymodels._
import scala.collection.mutable.Map
import games.{OhHell, JustOne}
import scala.util.{Try, Success, Failure}
import scala.language.postfixOps

object LobbyManager {
  def props(
      lobbyId: String,
      environment: Environment,
      onDisband: () => Unit
  ) =
    Props(
      new LobbyManager(lobbyId, environment, onDisband)
    )
}

case class User(id: String, actor: ActorRef, var isActive: Boolean = true)

class LobbyManager(
    val id: String,
    val environment: Environment,
    val onDisband: () => Unit
) extends Actor {
  import context._

  val users: Map[String, User] = Map.empty
  var gameType: GameType = games.GameType.justOne
  var game: Game = null

  // if we don't receive mesages after 5 mins, timeout
  context.setReceiveTimeout(5 minutes)

  def baseReceive: Receive = {
    case Ping => {} //ignore pings
    case GetUsers =>
      sender() ! LobbyUsersMsg(id, users.keys.toList)
  }

  def receive: Receive = baseReceive orElse {
    case Join(user, out) =>
      println(f"User ${user} joined lobby ${id}")
      users.put(user, User(user, out))
      users.foreach(_._2.actor ! LobbyUsersMsg(id, users.map(_._1).toList))
      sendAll(LobbyGameTypeMsg(gameType))
    case Leave(user) =>
      println(f"User ${user} left lobby ${id}")
      users.remove(user)
      users.foreach(_._2.actor ! LobbyUsersMsg(id, users.map(_._1).toList))
      disbandIfNeeded()
    case SetGame(newGameType) =>
      println(f"Set Game to type: ${newGameType}")
      gameType = newGameType
      sendAll(LobbyGameTypeMsg(newGameType))
    case msg: StartGame =>
      println("Start Game!")
      val players = users.keySet.toList
      game = gameType match {
        case games.GameType.ohHell  => new OhHell()
        case games.GameType.justOne => new JustOne(environment)
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
        users(user).isActive = true
        sendAll(LobbyGameTypeMsg(gameType))
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
      users(user).isActive = false
      disbandIfNeeded()
    case GetState(user) =>
      println(f"sending state to user ${user}")
      sender() ! LobbyStateMsg(game.getState().toJsonWithFilter(user))
    case action: PlayerAction =>
      val fromPlayer = action.player
      val result = game
        .parseAction(action)
        .flatMap(move => game.receiveAction(fromPlayer, move))

      result match {
        case Success(_) => sendState()
        case Failure(e) => {
          sendError(fromPlayer, e.getMessage())
          println("Failure in Processing Action:")
          e.printStackTrace()
        }
      }
  }

  def sendFailureIfFail(user: String, attempt: Try[_]) = {
    attempt match {
      case Failure(exception) => sendError(user, exception.getMessage())
      case Success(value)     => sendState()
    }
  }

  def sendAll(message: LobbyMsg) = users.foreach(_._2.actor ! message)

  def sendError(user: String, message: String) = {
    users(user).actor ! ErrorMsg("unknown", message)
  }

  def sendState() = {
    val state = game.getState()
    println("Sending state")
    users.foreach(tuple =>
      tuple._2.actor ! LobbyStateMsg(state.toJsonWithFilter(tuple._1))
    )
  }

  def disbandIfNeeded(): Unit = {
    if (users.isEmpty || !users.exists({ case (_, user) => user.isActive })) {
      println("all users left, disband the lobby")
      onDisband()
      context.stop(self)
    }
  }
}
