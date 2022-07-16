package websockets

import akka.actor._
import akka.actor.typed.scaladsl
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import models.lobbymodels._
import play.api.libs.json._
import auth.AuthService
import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.ReceiveTimeout

object LobbyUserActor {
  def props(
      out: ActorRef,
      manager: ActorRef,
      authService: AuthService
  ) = Props(
    new LobbyUserActor(out, manager, authService)
  )
}

class LobbyUserActor(
    out: ActorRef,
    manager: ActorRef,
    authService: AuthService
) extends Actor {
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
              (data \ "UserId").asOpt[String] match {
                case Some(id) => {
                  userId = id
                  println("userid auth success")
                  // user id auth path
                  context.setReceiveTimeout(300 seconds)
                  manager ! Join(id, self)
                  become(lobby)
                }
                case None =>
                  println("userid auth failed")
                  self ! PoisonPill
              }
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
        case "GetUsers" =>
          println("get users msg received")
          manager ! GetUsers()
        case "GetState" =>
          println("GetState Message Received")
          manager ! GetState(userId)
        case "GameAction" =>
          println("Game Action Received")
          data.validate[PlayerActionMsg].asOpt match {
            case Some(PlayerActionMsg(aType, data)) =>
              manager ! PlayerAction(userId, aType, data)
            case None => sendErrorMessage("Invalid data for GameAction Command")
          }
        case "Leave" =>
          println("Leave message received")
          manager ! Leave(user = userId)
        case _ =>
          println("Unknown Message Sent from client")
      }
  }

  def sendErrorMessage(message: String) = {
    out ! LobbyOutMsg(userId, "error", Json.obj("message" -> message))
  }
}
