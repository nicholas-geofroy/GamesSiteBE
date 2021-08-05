package managers

import models.usermodels._
import javax.inject.Singleton
import javax.inject.Inject
import play.api.Configuration

import org.mongodb.scala.bson.codecs.Macros
import org.bson.codecs.configuration.CodecRegistries.{
  fromRegistries,
  fromProviders,
  fromCodecs
}

import scala.collection.JavaConverters._
import org.mongodb.scala._
import org.mongodb.scala.model.Filters._
import scala.concurrent.{Future, ExecutionContext}
import db.mongo.{getDatabase, getBaseCodecRegistry}
import scala.jdk.FutureConverters._

@Singleton
class UserManager @Inject() (
    config: Configuration,
    authManager: Auth0Manager,
    implicit val ec: ExecutionContext
) {
  val userCodecProvider = Macros.createCodecProvider[User]()
  val codecRegistry =
    fromRegistries(fromProviders(userCodecProvider), getBaseCodecRegistry())

  val database: MongoDatabase = getDatabase().withCodecRegistry(codecRegistry)
  val userCollection: MongoCollection[User] = database.getCollection("users")

  def createUser(request: NewUserRequest): Future[User] = {
    println("Create User");
    val newUser = User(displayName = request.displayName)
    userCollection
      .insertOne(newUser)
      .head()
      .map(_ => {
        newUser
      })
  }

  def getUser(userId: String): Future[Option[User]] = {
    return authManager.getUser(userId)
  }
}
