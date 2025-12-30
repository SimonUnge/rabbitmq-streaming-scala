package rabbitmq.streaming

import org.apache.pekko.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._

object SubscriberSupervisor {

  sealed trait Command
  case class CreateSubscriber(
      connectionActor: ActorRef[ConnectionActor.Command],
      stream: String,
      offsetSpec: OffsetSpecification,
      initialCredit: Short,
      messageHandler: (Long, Array[Byte]) => Unit,
      replyTo: ActorRef[ActorRef[SubscriberActor.Command]]
  ) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("SubscriberSupervisor starting")
      running(subscribers = Map.empty, nextId = 0, Map.empty)
    }

  private def running(
      subscribers: Map[String, ActorRef[SubscriberActor.Command]],
      nextId: Int,
      subscriberIdByConnection: Map[ActorRef[ConnectionActor.Command], Byte]
  ): Behavior[Command] =
    Behaviors
      .receive[Command] { (context, message) =>
        message match {
          case CreateSubscriber(
                connectionActor,
                stream,
                offsetSpec,
                initialCredit,
                messageHandler,
                replyTo
              ) =>
            val childName = s"subscriber-${nextId}"
            val subscriberId =
              subscriberIdByConnection.getOrElse(connectionActor, 0.toByte)
            val supervised = Behaviors
              .supervise(
                SubscriberActor(
                  connectionActor,
                  subscriberId,
                  stream,
                  offsetSpec,
                  initialCredit,
                  messageHandler
                )
              )
              .onFailure[Exception](SupervisorStrategy.restart)

            val actorRef = context.spawn(supervised, childName)
            replyTo ! actorRef
            running(
              subscribers + (childName -> actorRef),
              nextId + 1,
              subscriberIdByConnection + (connectionActor -> (subscriberId + 1).toByte)
            )

          case _ =>
            Behaviors.unhandled
        }
      }
      .receiveSignal {
        case (context, org.apache.pekko.actor.typed.ChildFailed(ref, cause)) =>
          context.log.error("Subscriber child failed: {}", cause.getMessage)
          Behaviors.same
      }
}
