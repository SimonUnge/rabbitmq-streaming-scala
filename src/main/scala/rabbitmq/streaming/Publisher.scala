package rabbitmq.streaming

import org.apache.pekko.actor.typed.ActorRef
import org.apache.pekko.actor.typed.scaladsl.AskPattern._
import org.apache.pekko.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

class Publisher(actor: ActorRef[PublisherActor.Command])(implicit
    system: org.apache.pekko.actor.typed.ActorSystem[_]
) {
  implicit val timeout: Timeout = 5.seconds
  implicit val ec: ExecutionContext = system.executionContext

  def publish(data: Array[Byte]): Future[Unit] = {
    actor.ask(ref => PublisherActor.Publish(data, ref)).map {
      case PublisherActor.Published          => ()
      case PublisherActor.PublishFailed(err) =>
        throw new Exception(err)
    }
  }

  def close(): Unit = {
    actor ! PublisherActor.Close
  }
}
