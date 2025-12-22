package rabbitmq.streaming

import org.apache.pekko.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._

object PublisherSupervisor {

  sealed trait Command
  case class CreatePublisher(
      connectionActor: ActorRef[ConnectionActor.Command],
      stream: String,
      replyTo: ActorRef[ActorRef[PublisherActor.Command]]
  ) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("PublisherSupervisor starting")
      running(
        publishers = Map.empty,
        nextId = 0,
        publisherIdByConnection = Map.empty
      )
    }

  private def running(
      publishers: Map[String, ActorRef[PublisherActor.Command]],
      nextId: Int,
      publisherIdByConnection: Map[ActorRef[ConnectionActor.Command], Byte]
  ): Behavior[Command] =
    Behaviors
      .receive[Command] { (context, message) =>
        message match {
          case CreatePublisher(connectionActor, stream, replyTo) =>
            val childName = s"publisher-${nextId}"
            val publisherId =
              publisherIdByConnection.getOrElse(connectionActor, 0.toByte)
            val supervised = Behaviors
              .supervise(PublisherActor(connectionActor, publisherId, stream))
              .onFailure[Exception](SupervisorStrategy.restart)

            val actorRef = context.spawn(supervised, childName)
            replyTo ! actorRef

            running(
              publishers + (childName -> actorRef),
              nextId + 1,
              publisherIdByConnection + (connectionActor -> (publisherId + 1).toByte)
            )
          case _ =>
            Behaviors.unhandled
        }
      }
      .receiveSignal {
        case (context, org.apache.pekko.actor.typed.ChildFailed(ref, cause)) =>
          context.log.error("Publisher child failed: {}", cause.getMessage)
          Behaviors.same
      }
}
