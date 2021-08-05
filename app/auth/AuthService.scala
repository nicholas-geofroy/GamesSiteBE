package auth

import javax.inject.Inject
import com.auth0.jwk.UrlJwkProvider
import pdi.jwt.{JwtAlgorithm, JwtBase64, JwtClaim, JwtJson}
import play.api.Configuration
import scala.util.{Failure, Success, Try}
import play.api.mvc.Result
import akka.stream.scaladsl.Flow
import play.api.mvc.RequestHeader
import scala.concurrent.Future
import play.api.mvc.Results
import play.api.mvc.Headers
import play.api.http.HeaderNames
import play.api.libs.json.JsObject
import play.api.libs.json.Json

class AuthService @Inject() (config: Configuration) {
  implicit val clock = java.time.Clock.systemUTC

  // A regex that defines the JWT pattern and allows us to
  // extract the header, claims and signature
  private val jwtRegex = """(.+?)\.(.+?)\.(.+?)""".r

  // Your Auth0 domain, read from configuration
  private def domain = config.get[String]("auth0.domain")

  // Your Auth0 audience, read from configuration
  private def audience = config.get[String]("auth0.audience")

  // The issuer of the token. For Auth0, this is just your Auth0
  // domain including the URI scheme and a trailing slash.
  private def issuer = s"https://$domain/"

  // A regex for parsing the Authorization header value
  private val headerTokenRegex = """Bearer (.+?)""".r

  def authWebSocket[In, Out](
      f: String => Either[Result, Flow[In, Out, _]]
  ): RequestHeader => Future[Either[Result, Flow[In, Out, _]]] = { request =>
    println("Websocket request!")
    validateHeaders(request.headers) match {
      case Left(result) => Future.successful(Left(result))
      case Right(claim) => {
        claim.subject match {
          case Some(userId) => Future.successful(f(userId))
          case None =>
            Future.successful(Left(Results.Unauthorized("No subject in jwt")))
        }
      }
    }
  }

  def validateHeaders(headers: Headers): Either[Result, JwtClaim] = {
    headers
      .get(HeaderNames.AUTHORIZATION)
      .map(token => validateToken(token))
      .getOrElse(
        Left(
          Results
            .Unauthorized(Json.obj("message" -> "no auth token in headers"))
        )
      )
  }

  def validateToken(token: String): Either[Result, JwtClaim] = {
    extractBearerToken(token)
      .map {
        validateBearerToken(_)
      }
      .getOrElse(Left(Results.Unauthorized))
  }

  private def validateBearerToken(token: String): Either[Result, JwtClaim] = {
    validateJwt(token) match {
      case Success(claim) => Right(claim)
      case Failure(t)     => Left(Results.Unauthorized(t.getMessage()))
    }
  }

  private def extractBearerToken(token: String): Option[String] = {
    Option(token) collect { case headerTokenRegex(token) =>
      token
    }
  }

  def validateJwt(token: String): Try[JwtClaim] = for {
    jwk <- getJwk(token) // Get the secret key for this token
    claims <- JwtJson.decode(
      token,
      jwk.getPublicKey,
      Seq(JwtAlgorithm.RS256)
    ) // Decode the token using the secret key
    _ <- validateClaims(claims) // validate the data stored inside the token
  } yield claims

  // Splits a JWT into it's 3 component parts
  private val splitToken = (jwt: String) =>
    jwt match {
      case jwtRegex(header, body, sig) => Success((header, body, sig))
      case _ =>
        Failure(new Exception("Token does not match the correct pattern"))
    }

  // As the header and claims data are base64-encoded, this function
  // decodes those elements
  private val decodeElements = (data: Try[(String, String, String)]) =>
    data map { case (header, body, sig) =>
      (JwtBase64.decodeString(header), JwtBase64.decodeString(body), sig)
    }

  // Gets the JWK from the JWKS endpoint using the jwks-rsa library
  private val getJwk = (token: String) =>
    (splitToken andThen decodeElements)(token) flatMap { case (header, _, _) =>
      val jwtHeader = JwtJson.parseHeader(header) // extract the header
      val jwkProvider = new UrlJwkProvider(s"https://$domain")

      // Use jwkProvider to load the JWKS data and return the JWK
      jwtHeader.keyId.map { k =>
        Try(jwkProvider.get(k))
      } getOrElse Failure(new Exception("Unable to retrieve kid"))
    }

  private val validateClaims = (claims: JwtClaim) =>
    if (claims.isValid(issuer, audience)) {
      Success(claims)
    } else {
      Failure(new Exception("The JWT did not pass validation"))
    }
}
