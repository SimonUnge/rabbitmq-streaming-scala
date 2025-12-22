package rabbitmq.streaming

import org.apache.pekko.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration._

object ConnectionSupervisor {

  sealed trait Command
  case class CreateConnection(
      config: ConnectionConfig,
      replyTo: ActorRef[ActorRef[ConnectionActor.Command]]
  ) extends Command

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("ConnectionSupervisor starting")
      running(connections = Map.empty, nextId = 0)
    }

  private def running(
      connections: Map[String, ActorRef[ConnectionActor.Command]],
      nextId: Int
  ): Behavior[Command] =
    Behaviors
      .receive[Command] { (context, message) =>
        message match {
          case CreateConnection(config, replyTo) =>
            val childName = s"connection-${nextId}"
            val supervised = Behaviors
              .supervise(ConnectionActor(config))
              .onFailure[Exception](SupervisorStrategy.restart)
            val actorRef = context.spawn(supervised, childName)
            replyTo ! actorRef
            running(connections + (childName -> actorRef), nextId + 1)
          case _ =>
            Behaviors.unhandled
        }
      }
      .receiveSignal {
        case (context, org.apache.pekko.actor.typed.ChildFailed(ref, cause)) =>
          context.log.error("Connection child failed: {}", cause.getMessage)
          // Child will be restarted by supervisor strategy
          Behaviors.same
      }
}
