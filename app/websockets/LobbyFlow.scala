// package websockets

// import akka.stream.stage.GraphStage
// import akka.stream.FlowShape
// import models.lobbymodels._
// import akka.stream.Inlet
// import akka.stream.Outlet
// import org.reactivestreams.Publisher
// import org.reactivestreams.Subscriber
// import akka.stream.Attributes
// import akka.stream.stage.GraphStageLogic
// import akka.stream.stage.InHandler
// import akka.stream.stage.OutHandler

// class LobbyFlow extends GraphStage[FlowShape[LobbyInMsg, LobbyOutMsg]] {
//   val in = Inlet[LobbyInMsg]("lobby.inmsg")
//   val out = Outlet[LobbyOutMsg]("lobby.outmsg")

//   override val shape = FlowShape.of(in, out)

//   override def createLogic(attr: Attributes): GraphStageLogic =
//     new GraphStageLogic(shape) {

//       setHandler(
//         in,
//         new InHandler {
//           override def onPush(): Unit = {
//             push(out, f(grab(in)))
//           }
//         }
//       )
      
//       setHandler(
//         out,
//         new OutHandler {
//           override def onPull(): Unit = {
//             pull(in)
//           }
//         }
//       )
//     }

// }
