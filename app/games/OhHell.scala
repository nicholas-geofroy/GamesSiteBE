package games

import scala.util.Random
import scala.util.{Try, Success, Failure}
import play.api.libs.json.JsObject
import play.api.libs.json._
import play.api.libs.functional.syntax._
import websockets.PlayerAction

object Suit {
  type Suit = String
  val Hearts = "H"
  val Clubs = "C"
  val Diamonds = "D"
  val Spades = "S"
  val NoSuit = "N"

  def apply(strVal: String): Suit = new Suit(strVal)
  def unapply(suit: Suit): Option[(String)] = Some((suit))
}

import Suit._

object Rank extends Enumeration {
  type Rank = Value
  val Ace = Value(1)
  val Two = Value(2)
  val Three = Value(3)
  val Four = Value(4)
  val Five = Value(5)
  val Six = Value(6)
  val Seven = Value(7)
  val Eight = Value(8)
  val Nine = Value(9)
  val Ten = Value(10)
  val Jack = Value(11)
  val Queen = Value(12)
  val King = Value(13)
  val Joker = Value(14)

}

import Rank._

case class Card(suit: Suit, rank: Rank) {
  override def toString(): String = {
    return suit + rank
  }
}
object Card {
  def fromJson(suit: String, rank: Int): Card = {
    Card(suit, Rank(rank))
  }

  def unapplyJson(card: Card): Option[(String, Int)] = Some(
    (card.suit, card.rank.id)
  )

  implicit val reads: Reads[Card] = (
    (JsPath \ "suit").read[String] and
      (JsPath \ "rank").read[Int]
  )(Card.fromJson _)

  implicit val writes: Writes[Card] = (
    (JsPath \ "suit").write[String] and
      (JsPath \ "rank").write[Int]
  )(unlift(Card.unapplyJson))

  implicit val format: Format[Card] = Format(reads, writes)
}

class NotInHandException extends RuntimeException
class WrongPlayerException extends RuntimeException

class Hand(val cards: List[Card]) {
  var _cards = cards
  def playCard(toRemove: Card): Try[Card] = {
    if (_cards.contains(toRemove)) {
      _cards = _cards.filter(c => c != toRemove)
      return Success(toRemove)
    }
    return Failure(new NotInHandException())
  }
}

object Hand {
  implicit val reads: Reads[Hand] = (JsPath.read[List[Card]]).map(Hand.apply _)
  implicit val writes: Writes[Hand] =
    Writes[Hand](h => JsArray(h._cards.map(Json.toJson(_))))
  implicit val format = Format(reads, writes)

  def apply(initCards: List[Card]) = new Hand(initCards)
  def unapply(hand: Hand): Option[(List[Card])] = Some((hand._cards))
}

class Trick private (_playedCards: List[(String, Card)] = List.empty) {
  var playedCards = _playedCards

  def addCard(player: String, card: Card) = {
    playedCards = playedCards :+ (player, card)
  }
}

object Trick {
  implicit val format = Json.format[Trick]

  def apply() = new Trick(List.empty)
  def apply(initCards: List[(String, Card)]) = new Trick(initCards)
  def unapply(trick: Trick): Option[(List[(String, Card)])] = Some(
    (trick.playedCards)
  )
}

case class GlobalPlayerState(var points: Int = 0)
object GlobalPlayerState {
  implicit val format = Json.format[GlobalPlayerState]
}

case class HandPlayerState(hand: Hand, var bet: Int = -1, var tricks: Int = 0) {
  def getPrivate(): HandPlayerState = {
    HandPlayerState(Hand(List.empty), bet, tricks)
  }
}
object HandPlayerState {
  implicit val format = Json.format[HandPlayerState]
}
case class TableState(val trump: Suit)
object TableState {
  implicit val format = Json.valueFormat[TableState]
}

class OhHellState private (
    initPlayers: List[String],
    globalState: Map[String, GlobalPlayerState],
    initRoundNum: Int,
    initRound: Round
) extends GameState {
  // list of players randomly shuffled to produce an ordering
  val players = initPlayers
  val numPlayers = players.length

  // global state for each player
  val globalPlayerState: Map[String, GlobalPlayerState] = globalState

  // state for each player specific to a single round
  var round: Round = initRound
  var roundNum = initRoundNum

  def copy(): OhHellState =
    new OhHellState(initPlayers, globalState, roundNum, round.copy())

  def toJsonWithFilter(player: String) = OhHellState.toJsonWithFilter(this, player)
}

object OhHellState {
  def fromExisting(
      players: List[String],
      globalState: Map[String, GlobalPlayerState],
      roundNum: Int,
      round: Round
  ): OhHellState = {
    new OhHellState(players, globalState, roundNum, round)
  }

  def newState(initPlayers: List[String]): OhHellState = {
    new OhHellState(
      Random.shuffle(initPlayers),
      Map.from(initPlayers.map(id => (id, new GlobalPlayerState()))),
      0,
      null
    )
  }

  def unapply(
      gameState: OhHellState
  ): Option[(List[String], Map[String, GlobalPlayerState], Int, Round)] = Some(
    (
      gameState.players,
      gameState.globalPlayerState,
      gameState.roundNum,
      gameState.round
    )
  )

  def toJsonWithFilter(
      state: OhHellState,
      playerFilter: String = null
  ): JsValue = {
    val stateClone = state.copy()

    //filter out the hands of the non-local players
    if (playerFilter != null) {
      if (stateClone.round != null) {
        stateClone.round.localPlayerState =
          stateClone.round.localPlayerState.map { case (pId, handState) =>
            if (pId == playerFilter) (pId, handState)
            else (pId, handState.getPrivate())
          }
      }
    }
    return Json.toJson(stateClone)
  }

  implicit val reads: Reads[OhHellState] = (
    (JsPath \ "players").read[List[String]] and
      (JsPath \ "globalState").read[Map[String, GlobalPlayerState]] and
      (JsPath \ "roundNum").read[Int] and
      (JsPath \ "round").read[Round]
  )(OhHellState.fromExisting _)

  implicit val writes: Writes[OhHellState] = (
    (JsPath \ "players").write[List[String]] and
      (JsPath \ "globalState").write[Map[String, GlobalPlayerState]] and
      (JsPath \ "roundNum").write[Int] and
      (JsPath \ "round").write[Round]
  )(unlift(OhHellState.unapply))

  implicit val format = Format(reads, writes)
}

class Round private (
    _top: Card,
    _playerState: Map[String, HandPlayerState],
    _tricks: List[Trick],
    _nextPlayerIdx: Int,
    _firstPlayer: String,
    _cardsPerPlayer: Int
) {
  val top = _top
  val trump = _top.suit
  var localPlayerState = _playerState
  var tricks = _tricks
  var nextPlayerIdx: Int = _nextPlayerIdx
  val firstPlayer = _firstPlayer
  val cardsPerPlayer = _cardsPerPlayer

  def copy(): Round = new Round(
    top,
    localPlayerState,
    tricks,
    nextPlayerIdx,
    firstPlayer,
    cardsPerPlayer
  )
}

object Round {
  def newRound(
      gameState: OhHellState,
      initDeck: List[Card],
      cardsPerPlayer: Int
  ): Round = {
    val top = initDeck.head
    val deck = initDeck.tail
    val numPlayers = gameState.numPlayers
    val players = gameState.players
    val playerHands = (0 until numPlayers)
      .map(i =>
        (
          players(i),
          new Hand(deck.slice(i * cardsPerPlayer, (i + 1) * cardsPerPlayer))
        )
      )
      .toMap
    val localPlayerState =
      playerHands.view.mapValues(hand => HandPlayerState(hand)).toMap
    var tricks: List[Trick] = List(Trick())
    var nextPlayerIdx: Int = gameState.roundNum % numPlayers
    val firstPlayer = players(nextPlayerIdx)

    return new Round(
      top,
      localPlayerState,
      tricks,
      nextPlayerIdx,
      firstPlayer,
      cardsPerPlayer
    )
  }

  def fromExisting(
      top: Card,
      playerState: Map[String, HandPlayerState],
      tricks: List[Trick],
      nextPlayerIdx: Int,
      firstPlayer: String,
      cardsPerPlayer: Int
  ): Round = new Round(
    top,
    playerState,
    tricks,
    nextPlayerIdx,
    firstPlayer,
    cardsPerPlayer
  )

  def unapply(
      round: Round
  ): Option[
    (Card, Map[String, HandPlayerState], List[Trick], Int, String, Int)
  ] =
    Some(
      (
        round.top,
        round.localPlayerState,
        round.tricks,
        round.nextPlayerIdx,
        round.firstPlayer,
        round.cardsPerPlayer
      )
    )

  implicit val reads: Reads[Round] = (
    (JsPath \ "top").read[Card] and
      (JsPath \ "playerState").read[Map[String, HandPlayerState]] and
      (JsPath \ "tricks").read[List[Trick]] and
      (JsPath \ "nextPlayerIdx").read[Int] and
      (JsPath \ "firstPlayer").read[String] and
      (JsPath \ "cardsPerPlayer").read[Int]
  )(Round.fromExisting _)

  implicit val writes: Writes[Round] = (
    (JsPath \ "top").write[Card] and
      (JsPath \ "playerState").write[Map[String, HandPlayerState]] and
      (JsPath \ "tricks").write[List[Trick]] and
      (JsPath \ "nextPlayerIdx").write[Int] and
      (JsPath \ "firstPlayer").write[String] and
      (JsPath \ "cardsPerPlayer").write[Int]
  )(unlift(Round.unapply))

  implicit val format = Format(reads, writes)
}

case class LobbyState(players: List[String])
object LobbyState {
  implicit val format = Json.format[LobbyState]
}

class OhHell() extends Game {
  var state: OhHellState = null
  val numCards = 52
  val unsortedDeck = Deck.defaultDeck

  def parseAction(msg: PlayerAction): Try[Move] = {
    Failure(new Throwable())
  }

  def receiveAction(fromPlayer: String, action: Move): Try[Unit] = {
    Failure(new Throwable())
  }

  def isValid(players: List[String]): Boolean = {
    players.length > 1
  }

  def tryInit(players: List[String]): Try[Unit] = {
    if (!isValid(players)) {
      return Failure(new InvalidMsgException())
    }
    state = OhHellState.newState(players)
    Success(nextRound())
  }

  def nextRound(): Unit = {
    state.roundNum += 1
    val deck = Random.shuffle(unsortedDeck)
    state.round = Round.newRound(state, deck, state.roundNum)
  }

  def getNextPlayer(): String = {
    return state.players(state.round.nextPlayerIdx)
  }

  def setNextPlayer(): String = {
    state.round.nextPlayerIdx =
      (state.round.nextPlayerIdx + 1) % state.numPlayers
    return getNextPlayer()
  }

  def isGameOver(): Boolean = {
    return state.roundNum * state.numPlayers >= numCards
  }

  def play(playerId: String, card: Card): Try[Unit] = {
    val round = state.round
    val trick = round.tricks.last
    val trump = round.trump

    if (playerId != getNextPlayer()) {
      return Failure(new WrongPlayerException())
    }

    return round
      .localPlayerState(playerId)
      .hand
      .playCard(card)
      .map(card => {
        trick.addCard(playerId, card)
        // if we played the last card in the trick, calculate winner
        if (trick.playedCards.length == state.numPlayers) {
          val trickWinner = determineTrickWinner(trick.playedCards, trump)
          round.localPlayerState(trickWinner).tricks += 1
          // if we played the last trick, end the round
          if (round.tricks.length == state.roundNum) {
            endRound()
          }
          round.tricks = round.tricks :+ Trick()
        }
        setNextPlayer()
      })
  }

  def endRound() = {
    //determine how many points each player receives
    val cardsPerPlayer = state.roundNum
    for ((player, localState) <- state.round.localPlayerState) {
      val globalState = state.globalPlayerState(player)
      globalState.points += getPoints(
        localState.bet,
        localState.tricks,
        cardsPerPlayer
      )
    }
    nextRound()
  }

  def bet(playerId: String, bet: Int): Try[Unit] = {
    if (playerId != getNextPlayer()) {
      return Failure(new WrongPlayerException())
    }

    val round = state.round
    round.localPlayerState(playerId).bet = bet
    val nextPlayer = setNextPlayer()
    return Success(())
  }

  def determineTrickWinner(
      playedCards: List[(String, Card)],
      trump: Suit
  ): String = {
    var bestPlay = playedCards.head
    val cardLed = bestPlay._2

    for (play <- playedCards.tail) {
      val playedCard = play._2
      val bestCard = bestPlay._2

      if (playedCard.suit == bestCard.suit && playedCard.rank > bestCard.rank) {
        bestPlay = play
      } else if (playedCard.suit == trump && bestCard.suit != trump) {
        bestPlay = play
      }
    }

    return bestPlay._1
  }

  def getPoints(bet: Int, tricksTaken: Int, numTricks: Int): Int = {
    if (bet == tricksTaken) {
      if (bet == 0) {
        return 5 + numTricks
      } else {
        return 10 + tricksTaken
      }
    } else {
      return 0
    }
  }

  def getState(): OhHellState = {
    return state
  }
}
