package db

import org.mongodb.scala.{MongoDatabase, MongoClient}
import org.bson.codecs.configuration.CodecRegistry
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.bson.codecs.configuration.CodecRegistries.{
  fromRegistries,
  fromProviders,
  fromCodecs,
}
import org.bson.codecs.UuidCodec
import org.bson.UuidRepresentation
import play.api.Configuration

package object mongo {
  def getDatabase(config: Configuration): MongoDatabase = {
    val password = config.get[String]("mongodb.password")
    val uri: String =
      s"mongodb+srv://scala-app:$password@cardsite-db.zllsp.mongodb.net/myFirstDatabase?retryWrites=true&w=majority"
    System.setProperty("org.mongodb.async.type", "netty")
    val mongoClient = MongoClient(uri)
    mongoClient.getDatabase("test")
  }

  def getBaseCodecRegistry(): CodecRegistry = {
      fromRegistries(fromCodecs(new UuidCodec(UuidRepresentation.STANDARD)), DEFAULT_CODEC_REGISTRY)
  }
}
