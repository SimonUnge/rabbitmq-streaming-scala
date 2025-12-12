package rabbitmq.streaming

import org.apache.pekko.actor.typed.ActorSystem

object TestConnectionActor extends App {
  val config = ConnectionConfig(
    host = sys.env.getOrElse("RABBITMQ_HOST", "localhost"),
    port = sys.env.get("RABBITMQ_PORT").map(_.toInt).getOrElse(5552),
    username = sys.env.getOrElse("RABBITMQ_USERNAME", "guest"),
    password = sys.env.getOrElse("RABBITMQ_PASSWORD", "guest")
  )

  val system = ActorSystem(ConnectionActor(config), "test-connection")

  println("Press ENTER to stop...")
  scala.io.StdIn.readLine()

  system.terminate()
}
