package rabbitmq.streaming

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import scala.concurrent.duration._
import scala.concurrent.Await

object PublisherActor {
  sealed trait Command

  case class Publish(
      data: Array[Byte],
      replyTo: ActorRef[PublishResult]
  ) extends Command
  case class PublishConfirmReceived(
      publishingIds: List[Long]
  ) extends Command
  case class PublishErrorReceived(
      errors: List[PublishingError]
  ) extends Command
  case object Close extends Command

  sealed trait PublishResult
  case object Published extends PublishResult
  case class PublishFailed(error: String) extends PublishResult

  def apply(
      connectionActor: ActorRef[ConnectionActor.Command],
      publisherId: Byte,
      stream: String
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info(
        "PublisherActor starting for stream: {}, publisherId: {}",
        stream,
        publisherId
      )
      val request: DeclarePublisherRequest = DeclarePublisherRequest(
        publisherId = publisherId,
        stream = stream,
        publisherReference = None
      )
      val future = connectionActor.ask(ref =>
        ConnectionActor.SendDeclarePublisher(request, ref)
      )(timeout = 5.seconds, scheduler = context.system.scheduler)

      try {
        val response = Await.result(future, 5.seconds)
        response.responseCode match {
          case Protocol.ResponseCodes.OK =>
            context.log.info("Publisher declared successfully")
            connectionActor ! ConnectionActor.RegisterPublisher(
              publisherId,
              context.self
            )
            ready(connectionActor, publisherId, 0L, Map.empty)
          case code =>
            context.log.error(s"Publisher declare failed with code: {code}")
            Behaviors.stopped
        }
      } catch {
        case e: Exception =>
          context.log.error(s"Declare failed:{e.getMessage}")
          Behaviors.stopped
      }
    }
  private def ready(
      connectionActor: ActorRef[ConnectionActor.Command],
      publisherId: Byte,
      publishingId: Long,
      pending: Map[Long, ActorRef[PublishResult]]
  ): Behavior[Command] =
    Behaviors
      .receive[Command] { (context, message) =>
        message match {
          case Publish(data, replyTo) =>
            val nextId = publishingId + 1
            val msgs = List(PublishedMessage(nextId, data))
            val request = PublishRequest(publisherId, msgs)
            connectionActor ! ConnectionActor.SendPublish(request)
            ready(
              connectionActor,
              publisherId,
              nextId,
              pending + (nextId -> replyTo)
            )
          case PublishConfirmReceived(publishingIds) =>
            publishingIds.foreach { id =>
              pending.get(id) match {
                case Some(actor) =>
                  actor ! Published
                case None =>
                  context.log.warn(
                    "Received Publish Confirm with unknown ID: {}",
                    id
                  )
              }
            }
            ready(
              connectionActor,
              publisherId,
              publishingId,
              pending -- publishingIds
            )
          case PublishErrorReceived(errors) =>
            errors.foreach { publishError =>
              pending.get(publishError.publishingId) match {
                case Some(actor) =>
                  actor ! PublishFailed(
                    s"Error code: ${publishError.errorCode}"
                  )
                case None =>
                  context.log.warn(
                    s"Received error code ${publishError.errorCode} for unknown ID ${publishError.publishingId}"
                  )
              }
            }
            val errorIds = errors.map(_.publishingId)
            ready(
              connectionActor,
              publisherId,
              publishingId,
              pending -- errorIds
            )
          case Close =>
            connectionActor ! ConnectionActor.SendDeletePublisher(
              DeletePublisherRequest(publisherId)
            )
            pending.values.foreach { replyTo =>
              replyTo ! PublishFailed("Publisher closed")
            }
            Behaviors.stopped
          case _ =>
            Behaviors.unhandled
        }
      }
      .receiveSignal { case (context, org.apache.pekko.actor.typed.PostStop) =>
        context.log.info("PublisherActor stopping")
        pending.values.foreach { replyTo =>
          replyTo ! PublishFailed("Actor stopped")
        }
        Behaviors.same
      }
}
