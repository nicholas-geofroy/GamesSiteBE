package controllers

import javax.inject.Inject
import play.api.mvc.ControllerComponents
import play.api.mvc.BaseController
import play.api.mvc._
import javax.inject.Singleton
import models.usermodels._
import play.api.libs.json.Json
import play.api.libs.json.JsError
import managers.UserManager
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success
import scala.util.Failure
import scala.util.Try

@Singleton
class UserController @Inject() (
    val cc: ControllerComponents,
    val um: UserManager,
    implicit val ec: ExecutionContext
) extends AbstractController(cc) {
  def getUserDisplayName(userId: String) = Action.async { request =>
    println(f"get user ${userId}")
    um.getUser(userId)
      .transform {
        case Success(user) =>
          Try(Ok(Json.toJson(user)))
        case Failure(exception) =>
          println("Error in getUserDisplayName: ")
          println(exception)
          Try(NotFound)
      }
  }
}
