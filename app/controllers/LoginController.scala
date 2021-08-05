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

@Singleton
class LoginController @Inject() (
    val cc: ControllerComponents,
    val um: UserManager,
    implicit val ec: ExecutionContext
) extends AbstractController(cc) {
  def register() = Action.async(parse.json) { request =>
    val userRequest = request.body.validate[NewUserRequest]
    userRequest.fold(
      errors => {
        Future {
          BadRequest(Json.obj("message" -> JsError.toJson(errors)))
        }
      },
      user => {
        um.createUser(user)
          .map(result => {
            Ok(
              Json.toJson(result)
            )
          })
      }
    )
  }
}
