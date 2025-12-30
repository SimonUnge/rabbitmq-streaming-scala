package rabbitmq.streaming

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

object RabbitMQSupervisor {
  sealed trait Command
  case class CreateConnection(
      config: ConnectionConfig,
      replyTo: ActorRef[ActorRef[ConnectionActor.Command]]
  ) extends Command
  case class CreatePublisher(
      connectionActor: ActorRef[ConnectionActor.Command],
      stream: String,
      replyTo: ActorRef[ActorRef[PublisherActor.Command]]
  ) extends Command
  case class CreateSubscriber(
      connectionActor: ActorRef[ConnectionActor.Command],
      stream: String,
      offsetSpec: OffsetSpecification,
      initialCredit: Short,
      messageHandler: (Long, Array[Byte]) => Unit,
      replyTo: ActorRef[ActorRef[SubscriberActor.Command]]
  ) extends Command
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
    Behaviors
      .receive[Command] { (context, message) =>
        message match {
          case CreateConnection(config, replyTo) =>
            connectionSupervisor ! ConnectionSupervisor.CreateConnection(
              config,
              replyTo
            )
            Behaviors.same
          case CreatePublisher(connection, stream, replyTo) =>
            publisherSupervisor ! PublisherSupervisor.CreatePublisher(
              connection,
              stream,
              replyTo
            )
            Behaviors.same
          case CreateSubscriber(
                connection,
                stream,
                offset,
                credit,
                handler,
                replyTo
              ) =>
            subscriberSupervisor ! SubscriberSupervisor.CreateSubscriber(
              connection,
              stream,
              offset,
              credit,
              handler,
              replyTo
            )
            Behaviors.same
          case Shutdown =>
            context.log.info("Shutting down RabbitMQ system")
            Behaviors.stopped
          case _ =>
            Behaviors.unhandled
        }
      }
      .receiveSignal { case (context, org.apache.pekko.actor.typed.PostStop) =>
        context.log.info("RabbitMQSupervisor stopped")
        Behaviors.same
      }
}
