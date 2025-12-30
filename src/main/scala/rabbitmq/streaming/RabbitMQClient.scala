package rabbitmq.streaming

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class RabbitMQClient(supervisor: ActorRef[RabbitMQSupervisor.Command])(implicit
    system: ActorSystem[_]
) {

  implicit val timeout: Timeout = 5.seconds
  implicit val ec: ExecutionContext = system.executionContext

  def createConnection(
      config: ConnectionConfig
  ): Future[ActorRef[ConnectionActor.Command]] = {
    supervisor.ask(ref => RabbitMQSupervisor.CreateConnection(config, ref))
  }

  def createPublisher(
      connection: ActorRef[ConnectionActor.Command],
      stream: String
  ): Future[Publisher] = {
    supervisor
      .ask(ref =>
        RabbitMQSupervisor
          .CreatePublisher(connection, stream, ref)
      )
      .map(actorRef => new Publisher(actorRef))
  }

  def createSubscriber(
      connection: ActorRef[ConnectionActor.Command],
      stream: String,
      offsetSpec: OffsetSpecification,
      initialCredit: Short = 10,
      messageHandler: (Long, Array[Byte]) => Unit
  ): Future[Subscriber] = {
    supervisor
      .ask(ref =>
        RabbitMQSupervisor.CreateSubscriber(
          connection,
          stream,
          offsetSpec,
          initialCredit,
          messageHandler,
          ref
        )
      )
      .map(actorRef => new Subscriber(actorRef))
  }

  def createStream(
      connection: ActorRef[ConnectionActor.Command],
      stream: String,
      arguments: Map[String, String] = Map.empty
  ): Future[Unit] = {
    connection
      .ask(ref =>
        ConnectionActor.SendCreate(CreateRequest(stream, arguments), ref)
      )
      .map {
        case CreateResponse(_, resCode)
            if resCode == Protocol.ResponseCodes.OK =>
          ()
        case CreateResponse(_, resCode) =>
          throw new Exception(s"Create failed: $resCode")
      }
  }

  def deleteStream(
      connection: ActorRef[ConnectionActor.Command],
      stream: String
  ): Future[Unit] = {
    connection
      .ask(ref => ConnectionActor.SendDelete(DeleteRequest(stream), ref))
      .map {
        case DeleteResponse(_, resCode)
            if resCode == Protocol.ResponseCodes.OK =>
          ()
        case DeleteResponse(_, resCode) =>
          throw new Exception(s"Delete failed: $resCode")
      }
  }

  def shutdown(): Unit = {
    supervisor ! RabbitMQSupervisor.Shutdown
  }
}

object RabbitMQClient {
  def apply(system: ActorSystem[_]): RabbitMQClient = {
    val supervisor =
      system.asInstanceOf[ActorRef[RabbitMQSupervisor.Command]]
    new RabbitMQClient(supervisor)(system)
  }

  def apply(): RabbitMQClient = {
    val system = ActorSystem(RabbitMQSupervisor(), "rabbitmq")
    apply(system)
  }
}
