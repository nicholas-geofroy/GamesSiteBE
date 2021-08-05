package managers

import javax.inject.Singleton
import play.api.Configuration
import scala.concurrent.ExecutionContext
import javax.inject.Inject
import scala.util.Try
import java.time.Instant
import play.api.libs.ws.WSClient
import scala.concurrent.Future
import play.api.libs.ws.WSResponse
import play.api.libs.json.Json
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import utils.getCurrentTimeSeconds
import scala.util.Success
import scala.util.Failure
import java.net.URLEncoder
import models.usermodels.User
import com.google.common.base.Charsets

case class TokenHolder(
    access_token: String,
    expires_in: Long,
    token_type: String
) {
  val start_time = getCurrentTimeSeconds()
  def expire_time() = {
    start_time + expires_in
  }
}
object TokenHolder {
  implicit val format = Json.format[TokenHolder]
}

case class Auth0User(user_id: String, name: String) {
  def toUser() = User(user_id, name)
}

object Auth0User {
  implicit val format = Json.format[Auth0User]
}

@Singleton
class Auth0Manager @Inject() (
    config: Configuration,
    ws: WSClient,
    implicit val ec: ExecutionContext
) {

  private val domain = config.get[String]("auth0.domain")
  private val clientId = config.get[String]("auth0.client-id")
  private val clientSecret = config.get[String]("auth0.client-secret")
  private val audience = f"https://${domain}/api/v2/"

  private var accessToken = fetchAccessToken()
  private def fetchAccessToken(): Future[TokenHolder] = {
    println("fetch access token")
    val future = ws
      .url(f"https://${domain}/oauth/token")
      .addHttpHeaders(
        "content-type" -> "application/x-www-form-urlencoded"
      )
      .post(
        Map(
          "grant_type" -> Seq("client_credentials"),
          "client_id" -> Seq(clientId),
          "client_secret" -> Seq(clientSecret),
          "audience" -> Seq(audience)
        )
      )
      .map { response =>
        response.json.validate[TokenHolder].get
      }

    future.onComplete {
      case Failure(exception) => println("error"); println(exception)
      case Success(value)     => println("token fetched")
    }

    future
  }

  private def updateAccessToken() = {
    fetchAccessToken().onComplete(_ match {
      case Success(newToken) =>
        accessToken = Future(newToken)
      case Failure(exception) =>
        println("Failure in fetching token")
        throw exception
    })
  }

  private def getAccessToken(): Future[TokenHolder] = {
    accessToken.flatMap(token => {
      if (getCurrentTimeSeconds() > token.expire_time() - 60) {
        updateAccessToken()
      }
      accessToken
    })
  }

  def getUser(userId: String): Future[Option[User]] = {
    getAccessToken()
      .flatMap(token => {
        val usersUrl = audience + f"users/${URLEncoder.encode(userId, Charsets.UTF_8)}" 
        ws.url(usersUrl)
          .addHttpHeaders(
            "authorization" -> f"Bearer ${token.access_token}"
          )
          .get()
      })
      .map(response => {
        println("get user success")
        response.json.validate[Auth0User].asOpt.map(_.toUser())
      })
  }
}
