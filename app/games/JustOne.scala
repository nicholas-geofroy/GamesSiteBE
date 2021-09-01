package games

import scala.util.Success
import scala.util.Failure
import scala.util.Try
import models.lobbymodels.LobbyInMsg
import play.api.libs.json.Format
import play.api.libs.json.Json
import play.api.libs.json.JsResult
import websockets.PlayerAction
import play.api.libs.json.JsValue
import play.api.libs.json.JsObject
import play.api.libs.json.JsPath
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads
import play.api.libs.json.JsArray
import play.api.libs.json.JsNumber

final case class Guess(guess: String) extends Move
object Guess {
  implicit val format = Json.format[Guess]
}
final case class Hint(hint: String) extends Move
object Hint {
  implicit val format = Json.format[Hint]
}
final case class NextRound() extends Move
final case class SetDuplicate(hint: String) extends Move
object SetDuplicate {
  implicit val format = Json.format[SetDuplicate]
}
final case class SetUnique(hint: String) extends Move
object SetUnique {
  implicit val format = Json.format[SetUnique]
}
final case class RevealHints() extends Move

case class HintState(hint: String, isDuplicate: Boolean = false)
object HintState {
  implicit val format = Json.format[HintState]
}

class JustOneState(
    val players: List[String],
    var roundNum: Int = 0,
    var roundStates: List[JustOneRoundState] = List.empty
) extends GameState {
  def curRound() = roundStates.last
  def toJsonWithFilter(player: String) = JsObject(
    Seq(
      "players" -> Json.toJson(players),
      "roundNum" -> JsNumber(roundNum),
      "roundStates" -> JsArray(
        (roundStates.dropRight(1) :+ curRound().filter(player))
          .map(x => Json.toJson(x)(JustOneRoundState.format))
      )
    )
  )
}

object JustOneState {}

case class JustOneRoundState(
    val guesser: String,
    var hints: Map[String, HintState],
    var guesses: List[String],
    var word: String,
    var correct: Boolean = false,
    var hintsSubmitted: Boolean = false,
    var hintsRevealed: Boolean = false
) {
  def getHints(): Iterable[String] = hints.values.map(_.hint)
  def isGuessCorrent(guess: String): Boolean = guess.equals(word)
  def filter(forPlayer: String): JustOneRoundState = {
    var filteredHints: Map[String, HintState] = Map.empty
    var meGuesser = forPlayer.equals(guesser)
    if (meGuesser) {
      if (!hintsRevealed) {
        filteredHints = hints.map(t => (t._1, HintState("", false))).toMap
      } else {
        filteredHints = hints
          .map(t => (t._1, if (t._2.isDuplicate) HintState("", true) else t._2))
          .toMap
      }
    } else {
      if (!hintsSubmitted) {
        filteredHints = hints.map(t => (t._1, HintState("", false))).toMap
      } else {
        filteredHints = hints
      }
    }

    new JustOneRoundState(
      guesser,
      filteredHints,
      guesses,
      if (meGuesser) "" else word,
      correct,
      hintsSubmitted,
      hintsRevealed
    )
  }
}

object JustOneRoundState {
  implicit val format = Json.format[JustOneRoundState]
}

class JustOne() extends Game {
  var state: JustOneState = null

  def getState() = state

  def tryInit(players: List[String]): Try[Unit] = {
    if (players.size < 2) {
      return Failure(new NotEnoughPlayers())
    }
    state = new JustOneState(players)
    nextRound()
    return Success(())
  }

  def receiveAction(fromPlayer: String, action: Move): Try[Unit] = {
    val round: JustOneRoundState = state.curRound()

    action match {
      case Guess(guess) =>
        println(f"Play ${fromPlayer} made guess ${guess}")
        if (!fromPlayer.equals(round.guesser)) {
          return Failure(new InvalidAction())
        }

        round.guesses = round.guesses :+ guess
        if (round.isGuessCorrent(guess)) {
          println("It was correct!")
          round.correct = true
        }

        return Success(())

      case Hint(hint) =>
        println(f"Received hint `${hint}` from ${fromPlayer}")
        // guesser can't give a hint
        if (fromPlayer.equals(round.guesser)) {
          println("Player can't hint because they are the guesser")
          return Failure(new InvalidAction())
        }

        round.hints = round.hints + (fromPlayer -> HintState(hint))

        // if all hints submitted then reveal the hints
        if (round.hints.size == state.players.size - 1) {
          println("all hints submitted, revealing...")
          val hintCounts = round.getHints().groupBy(x => x)
          round.hints = round.hints.map(e =>
            (e._1, HintState(e._2.hint, hintCounts(e._2.hint).size > 1))
          )
          round.hintsSubmitted = true
        }

        println("done handling hint")

        return Success(())

      case SetDuplicate(hintFromPlayer) =>
        if (fromPlayer.equals(round.guesser)) {
          return Failure(new InvalidAction())
        }

        return round.hints
          .get(hintFromPlayer)
          .map(hint => {
            round.hints =
              round.hints + (hintFromPlayer -> HintState(hint.hint, true))
            Success(())
          })
          .getOrElse(Failure(new InvalidAction()))

      case SetUnique(hintFromPlayer) =>
        if (fromPlayer.equals(round.guesser)) {
          return Failure(new InvalidAction())
        }

        return round.hints
          .get(hintFromPlayer)
          .map(hint => {
            round.hints =
              round.hints + (hintFromPlayer -> HintState(hint.hint, false))
            Success(())
          })
          .getOrElse(Failure(new InvalidAction()))
      case RevealHints() =>
        if (fromPlayer.equals(round.guesser)) {
          return Failure(new InvalidAction())
        }

        round.hints = round.hints.map(pair =>
          (pair._1, HintState(pair._2.hint, pair._2.isDuplicate))
        )
        round.hintsRevealed = true
        return Success(())
      case NextRound() =>
        nextRound()
        return Success(())
    }
  }

  def nextRound() = {
    val guesser = state.players(state.roundNum)
    state.roundStates = state.roundStates :+ new JustOneRoundState(
      guesser,
      Map.empty,
      List.empty,
      generateWord()
    )
    state.roundNum += 1
  }

  def generateWord(): String = {
    return "word"
  }

  def parseAction(msg: PlayerAction): Try[Move] = {
    val PlayerAction(_, msgType, data) = msg
    println(f"parse action of type ${msgType} data ${data.toString()}")
    msgType match {
      case "Guess" =>
        JsResult.toTry(data.validate[Guess], err => new InvalidMsgException())
      case "Hint" =>
        JsResult.toTry(data.validate[Hint], err => new InvalidMsgException())
      case "NextRound" => Success(NextRound())
      case "SetDuplicate" =>
        JsResult.toTry(
          data.validate[SetDuplicate],
          err => new InvalidMsgException()
        )
      case "SetUnique" =>
        JsResult.toTry(
          data.validate[SetUnique],
          err => new InvalidMsgException()
        )
      case "RevealHints" => Success(RevealHints())
    }
  }
}
