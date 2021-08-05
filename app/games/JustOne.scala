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

final case class Guess(guess: String) extends Move
object Guess {
  implicit val format = Json.format[Guess]
}
final case class Hint(hint: String) extends Move
object Hint {
  implicit val format = Json.format[Hint]
}
final case class NextRound() extends Move
final case class Hide(hint: String) extends Move
object Hide {
  implicit val format = Json.format[Hide]
}
final case class Show(hint: String) extends Move
object Show {
  implicit val format = Json.format[Show]
}
final case class RevealHints() extends Move

class JustOneState(val players: List[String]) extends GameState {
  var roundNum = 0
  var roundStates: List[JustOneRoundState] = List.empty
  def curRound() = roundStates.last

  def toJsonWithFilter(player: String) = JsObject(Map.empty[String, JsValue])
}

class JustOneRoundState(
    val guesser: String,
    var hints: Map[String, (String, Boolean)],
    var guesses: List[String],
    var correct: Boolean = false,
    var hintsRevealed: Boolean = false
) {
  def getHints(): Iterable[String] = hints.values.map(_._1.toLowerCase())
  def isGuessCorrent(guess: String): Boolean = getHints().exists(g => g.equals(guess))
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
        if (!fromPlayer.equals(round.guesser)) {
          return Failure(new InvalidAction())
        }

        round.guesses = round.guesses :+ guess
        if (round.isGuessCorrent(guess)) {
          round.correct = true
        }

        return Success(())

      case Hint(hint) =>
        // guesser can't give a hint
        if (fromPlayer.equals(round.guesser)) {
          return Failure(new InvalidAction())
        }

        round.hints = round.hints + (fromPlayer -> (hint, false))

        // if all hints submitted then reveal the hints
        if (round.hints.size == state.players.size - 1) {
          val hintCounts = round.getHints().groupBy(x=>x)
          // filter hints to the non duplicated ones
          round.hints = round.hints.map(e => (e._1, (e._2._1, hintCounts(e._1).size > 1)))
        }

        return Success(())
      
        case Hide(hint) => 
          if (fromPlayer.equals(round.guesser)) {
            return Failure(new InvalidAction())
          }

          round.hints = round.hints + (hint -> (round.hints(hint)._1, true))
          return Success(())
        case Show(hint) =>
          if (fromPlayer.equals(round.guesser)) {
            return Failure(new InvalidAction())
          }
          
          round.hints = round.hints + (hint -> (round.hints(hint)._1, false))
          return Success(())
        case RevealHints() =>
          if (fromPlayer.equals(round.guesser)) {
            return Failure(new InvalidAction())
          }

          round.hintsRevealed = true
          return Success(())
        case NextRound() =>
          nextRound()
          return Success(())
    }
  }

  def nextRound() = {
    val guesser = state.players(state.roundNum)
    state.roundStates = state.roundStates :+ new JustOneRoundState(guesser, Map.empty, List.empty)
    state.roundNum += 1
  }

  def parseAction(msg: PlayerAction): Try[Move] = {
    val PlayerAction(_, msgType, data) = msg
    msgType match {
      case "Guess" => JsResult.toTry(data.validate[Guess], err => new InvalidMsgException())
      case "Hint" => JsResult.toTry(data.validate[Hint], err => new InvalidMsgException())
      case "NextRound" => Success(NextRound())
      case "Hide" => JsResult.toTry(data.validate[Hide], err => new InvalidMsgException())
      case "Show" => JsResult.toTry(data.validate[Show], err => new InvalidMsgException())
      case "RevealHints" => Success(RevealHints())
    }
  }
}
