package rabbitmq.streaming

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object RabbitMQSupervisor {
  
  sealed trait Command
  case object Shutdown extends Command
  
  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("RabbitMQSupervisor starting")
      
      // Spawn the three child supervisors
      val connectionSupervisor = context.spawn(
        ConnectionSupervisor(),
        "connection-supervisor"
      )
      val publisherSupervisor = context.spawn(
        PublisherSupervisor(),
        "publisher-supervisor"
      )
      val subscriberSupervisor = context.spawn(
        SubscriberSupervisor(),
        "subscriber-supervisor"
      )
      
      context.log.info("All supervisors started")
      
      running(connectionSupervisor, publisherSupervisor, subscriberSupervisor)
    }
  
  private def running(
    connectionSupervisor: ActorRef[ConnectionSupervisor.Command],
    publisherSupervisor: ActorRef[PublisherSupervisor.Command],
    subscriberSupervisor: ActorRef[SubscriberSupervisor.Command]
  ): Behavior[Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case Shutdown =>
          context.log.info("Shutting down RabbitMQ system")
          Behaviors.stopped
        
        case _ =>
          Behaviors.unhandled
      }
    }
    .receiveSignal {
      case (context, org.apache.pekko.actor.typed.PostStop) =>
        context.log.info("RabbitMQSupervisor stopped")
        Behaviors.same
    }
}
