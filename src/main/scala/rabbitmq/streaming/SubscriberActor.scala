package rabbitmq.streaming

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import scala.concurrent.duration._
import scala.concurrent.Await

object SubscriberActor {
  sealed trait Command

  case class DeliverReceived(chunk: OsirisChunk) extends Command
  case class RequestCredit(credit: Short) extends Command
  case object Close extends Command

  sealed trait DeliveryResult
  case object MessageProcessed extends DeliveryResult
  case class MessageFailed(error: String) extends DeliveryResult

  def apply(
      connectionActor: ActorRef[ConnectionActor.Command],
      subscriptionId: Byte,
      stream: String,
      offsetSpec: OffsetSpecification,
      initialCredit: Short = 10,
      messageHandler: (Long, Array[Byte]) => Unit
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info(
        "SubscriberActor starting for stream: {}, subscriptionId: {}",
        stream,
        subscriptionId
      )

      connectionActor ! ConnectionActor.RegisterSubscriber(
        subscriptionId,
        context.self
      )

      val request = SubscribeRequest(
        subscriptionId,
        stream,
        offsetSpec,
        initialCredit,
        Map.empty
      )

      val future = connectionActor.ask(ref =>
        ConnectionActor.SendSubscribe(request, ref)
      )(timeout = 5.seconds, scheduler = context.system.scheduler)

      try {
        val response = Await.result(future, 5.seconds)
        response.responseCode match {
          case Protocol.ResponseCodes.OK =>
            context.log.info("Subscribe successfully")
            ready(
              connectionActor,
              subscriptionId,
              initialCredit,
              0,
              messageHandler
            )
          case code =>
            context.log.error(s"Subscribe failed with code: {}", code)
            Behaviors.stopped
        }
      } catch {
        case e: Exception =>
          context.log.error(s"Subscribe failed:{e.getMessage}")
          Behaviors.stopped
      }
    }

  private def ready(
      connectionActor: ActorRef[ConnectionActor.Command],
      subscriptionId: Byte,
      creditRequested: Short,
      creditUsed: Short,
      messageHandler: (Long, Array[Byte]) => Unit
  ): Behavior[Command] =
    Behaviors
      .receive[Command] { (context, message) =>
        message match {
          case DeliverReceived(chunk) =>
            chunk.chunkType match {
              case ChunkType.User =>
                context.log.info("Received {} messages", chunk.numEntries)
                val messages = ChunkParser.parse(chunk)
                messages.foreach { msg =>
                  messageHandler(msg.offset, msg.data)
                }
                val rawMessages = chunk.messages
                val numMessages = chunk.numEntries
                val newCreditUsed = (numMessages + creditUsed).toShort
                val newCreditRequested =
                  if (newCreditUsed > (creditRequested / 2)) {
                    val moreCredit: Short = 10
                    connectionActor ! ConnectionActor.SendCredit(
                      CreditRequest(subscriptionId, moreCredit)
                    )
                    (creditRequested + moreCredit).toShort
                  } else {
                    creditRequested
                  }
                ready(
                  connectionActor,
                  subscriptionId,
                  newCreditRequested,
                  newCreditUsed,
                  messageHandler
                )
              case _ =>
                ready(
                  connectionActor,
                  subscriptionId,
                  creditRequested,
                  creditUsed,
                  messageHandler
                )
            }
          case RequestCredit(credit) =>
            connectionActor ! ConnectionActor.SendCredit(
              CreditRequest(subscriptionId, credit)
            )
            ready(
              connectionActor,
              subscriptionId,
              (creditRequested + credit).toShort,
              creditUsed,
              messageHandler
            )
          case Close =>
            val request = UnsubscribeRequest(subscriptionId)
            connectionActor ! ConnectionActor.SendUnsubscribe(request)
            Behaviors.stopped
          case _ =>
            Behaviors.unhandled
        }
      }
      .receiveSignal { case (context, org.apache.pekko.actor.typed.PostStop) =>
        context.log.info("SubscriberActor stopping")
        Behaviors.same
      }
}
