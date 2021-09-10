package controllers
import javax.inject.Inject
import play.api.mvc.ControllerComponents
import play.api.mvc.BaseController
import play.api.mvc._
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import db.mongo.getDatabase
import org.mongodb.scala._
import models.gamemodels._
import org.mongodb.scala.bson.codecs.Macros
import org.bson.codecs.configuration.CodecRegistries.{
  fromRegistries,
  fromProviders
}
import db.mongo.getBaseCodecRegistry
import play.api.libs.json.Json
import scala.util.{Success, Failure}
import auth.{AuthAction, AuthService}
import akka.stream.scaladsl._
import play.api.libs.json.JsValue
import websockets.{LobbyActor, LobbyManager}
import play.api.libs.streams.ActorFlow
import akka.actor.ActorRef
import akka.actor.ActorSystem
import scala.concurrent.Future
import pdi.jwt.JwtClaim
import scala.collection.mutable
import models.lobbymodels._
import play.api.Configuration
import play.api.Environment

@Singleton
class LobbyController @Inject() (
    val cc: ControllerComponents,
    val config: Configuration,
    val authAction: AuthAction,
    val authService: AuthService,
    val environment: Environment,
    implicit val ec: ExecutionContext
)(implicit actorSystem: ActorSystem)
    extends AbstractController(cc) {
  val lobbyCodecProvider = Macros.createCodecProvider[Lobby]()
  val codecRegistry =
    fromRegistries(fromProviders(lobbyCodecProvider), getBaseCodecRegistry())
  val database: MongoDatabase =
    getDatabase(config).withCodecRegistry(codecRegistry)

  val lobbyCollection: MongoCollection[Lobby] =
    database.getCollection("lobbies")

  val lobbyManagers: mutable.Map[String, ActorRef] = mutable.Map.empty

  def createLobbyInDb() = authAction.async { request =>
    val userId = request.userId
    println(f"create lobby called by user: ${userId}")
    val lobby = Lobby(members = List(userId))
    lobbyCollection
      .insertOne(lobby)
      .head()
      .map(result => Ok(Json.toJson(lobby)))
      .recover { case e: Exception =>
        InternalServerError(Json.obj("message" -> e.getMessage()))
      }
  }

  implicit val messageFlowTransformer = WebSocket.MessageFlowTransformer
    .jsonMessageFlowTransformer[LobbyInMsg, LobbyOutMsg]

  def createLobby() = authAction(parse.json) { request =>
    val lobby = Lobby()
    val lobbyID = lobby.id.toString()

    val lobbyManagerRef = actorSystem.actorOf(
      LobbyManager.props(lobbyID, environment),
      "lobby." + lobbyID
    )
    lobbyManagers.put(lobby.id.toString(), lobbyManagerRef)
    Ok(Json.toJson(lobby))
  }

  def joinLobby(id: String) =
    WebSocket.acceptOrResult[LobbyInMsg, LobbyOutMsg] { request =>
      lobbyManagers.get(id) match {
        case Some(manager) =>
          Future(Right(ActorFlow.actorRef { out =>
            LobbyActor.props(out, manager, authService)
          }))
        case None =>
          Future(
            Left(
              Results.BadRequest(
                Json.obj("message" -> f"Invalid lobby id ${id}")
              )
            )
          )
      }
    }
}
