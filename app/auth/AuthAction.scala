package auth

import javax.inject.Inject
import pdi.jwt._
import play.api.http.HeaderNames
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.util.Try
import akka.stream.scaladsl.Flow

// A custom request type to hold our JWT claims, we can pass these on to the
// handling action
case class UserRequest[A](
    userId: String,
    jwt: JwtClaim,
    request: Request[A]
) extends WrappedRequest[A](request)

// Our custom action implementation
class AuthAction @Inject() (
    bodyParser: BodyParsers.Default,
    authService: AuthService
)(implicit ec: ExecutionContext)
    extends ActionBuilder[UserRequest, AnyContent] {

  override def parser: BodyParser[AnyContent] = bodyParser
  override protected def executionContext: ExecutionContext = ec

  // Called when a request is invoked. We should validate the bearer token here
  // and allow the request to proceed if it is valid.
  override def invokeBlock[A](
      request: Request[A],
      block: UserRequest[A] => Future[Result]
  ): Future[Result] =
    authService.validateHeaders(request.headers) match {
      // some sort of early return (failure)
      case Left(result) => Future.successful(result)
      case Right(claim) => {
          claim.subject match {
            case Some(subject) =>
              block(
                UserRequest(subject, claim, request)
              ) // token was valid - proceed!
            case None =>
              Future.successful(
                Results.InternalServerError("No subject in token")
              )
          }
        }
    }
}
