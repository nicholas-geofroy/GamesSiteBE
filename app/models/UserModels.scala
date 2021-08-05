package models
import play.api.libs.json._
import java.util.UUID


object usermodels {
    case class NewUserRequest(displayName: String)
    implicit val newUserRequestFormat = Json.reads[NewUserRequest]
    case class User(id: String = UUID.randomUUID().toString(), displayName: String)
    implicit val userFormat = Json.format[User]

    case class UserInfoRequest(userId: String)
    implicit val userInfoRequestFormat = Json.reads[UserInfoRequest]
}