package rabbitmq.streaming

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import java.nio.ByteBuffer
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}

object ConnectionActor {

  // TODO: Define message types
  sealed trait Command
  case object Close extends Command
  private case class FrameReceived(
      key: Short,
      version: Short,
      frame: ByteBuffer
  ) extends Command

  case class SendCreate(
      request: CreateRequest,
      replyTo: ActorRef[CreateResponse]
  ) extends Command
  case class SendDeclarePublisher(
      request: DeclarePublisherRequest,
      replyTo: ActorRef[DeclarePublisherResponse]
  ) extends Command
  case class SendPublish(request: PublishRequest)
      extends Command // Fire-and-forget

  sealed trait SendResult
  case object FrameSent extends SendResult
  case class SendFailed(error: String) extends SendResult

  def apply(config: ConnectionConfig): Behavior[Command] =
    Behaviors.setup { context =>
      implicit val ec: ExecutionContext = context.executionContext

      context.log.info("Connecting to {}:{}", config.host, config.port)

      try {
        val connection: Connection = new Connection(config)
        doHandshake(connection, config, context)
        context.log.info("Connected successfully")
        // NOTE: This is blocking I/O - for 1.0 migrate to Pekko Streams TCP
        Future {
          while (true) {
            val (key, version, frame) = connection.receiveFrame()
            context.self ! FrameReceived(key, version, frame)
          }
        }.onComplete {
          case Failure(exception) =>
            context.log.error("Frame reading failed: {}", exception.getMessage)
          case Success(_) =>
            context.log.info("Frame reading stopped")
        }

        //   - publishers = Map.empty (for later)
        //   - subscribers = Map.empty (for later)
        connected(connection, 0, Map.empty)
      } catch {
        case e: Exception =>
          context.log.error("Connection failed: {}", e.getMessage)
          Behaviors.stopped
      }
    }

  private def doHandshake(
      connection: Connection,
      config: ConnectionConfig,
      context: org.apache.pekko.actor.typed.scaladsl.ActorContext[Command]
  ): Unit = {
    // TODO: Initialize correlation ID counter (var corrId = 0)
    var correlationId = 0

    def receiveAndDecode[T](
        codec: (ByteBuffer, Short, Short) => Either[String, T],
        context: String
    ): T = {
      val (key, version, buffer) = connection.receiveFrame()
      codec(buffer, key, version) match {
        case Right(response) => response
        case Left(error)     => throw new Exception(s"$context failed: $error")
      }
    }

    correlationId += 1
    val peerPropsReq = PeerPropertiesRequest(
      Map(
        "product" -> "rabbitmq-streaming-scala",
        "version" -> "0.1.0",
        "platform" -> "Scala"
      )
    )
    connection.sendFrame(
      PeerPropertiesCodec.encode(peerPropsReq, correlationId)
    )

    receiveAndDecode(PeerPropertiesCodec.decode, "Peer Properties")
    context.log.info("Peer properties exchanged")

    correlationId += 1
    connection.sendFrame(SaslHandshakeCodec.encode(correlationId))
    receiveAndDecode(SaslHandshakeCodec.decode, "SASL Handshake")
    context.log.info("SASL handshake completed")

    correlationId += 1
    val saslData =
      s"\u0000${config.username}\u0000${config.password}".getBytes("UTF-8")
    val saslAuthReq = SaslAuthenticateRequest("PLAIN", saslData)
    connection.sendFrame(
      SaslAuthenticateCodec.encode(saslAuthReq, correlationId)
    )
    val authResp =
      receiveAndDecode(SaslAuthenticateCodec.decode, "SASL Authentication")
    if (authResp.responseCode != Protocol.ResponseCodes.OK) {
      throw new Exception(
        s"Authentication failed with code: ${authResp.responseCode}"
      )
    }
    context.log.info("Authentication successful")

    val tune: TuneRequest = receiveAndDecode(TuneCodec.decode, "Tune")
    context.log.info(
      s"Server tune: frameMax=${tune.frameMax}, heartbeat=${tune.heartbeat}"
    )
    connection.sendFrame(TuneCodec.encode(tune))
    context.log.info("Tune response sent")

    correlationId += 1
    val openReq = OpenRequest("/")
    connection.sendFrame(OpenCodec.encode(openReq, correlationId))
    val openResponse: OpenResponse =
      receiveAndDecode(OpenCodec.decode, "Open")
    if (openResponse.responseCode != Protocol.ResponseCodes.OK) {
      throw new Exception(
        s"Open failed with code: ${openResponse.responseCode}"
      )
    }
    context.log.info("Virtual host opened")
  }

  private def connected(
      connection: Connection,
      correlationId: Int,
      pending: Map[Int, ActorRef[_]]
  ): Behavior[Command] =
    Behaviors
      .receiveMessage[Command] {
        case SendCreate(request, replyTo) =>
          val nextId = correlationId + 1
          val buffer = CreateCodec.encode(request, nextId)
          connection.sendFrame(buffer)
          connected(connection, nextId, pending + (nextId -> replyTo))
        case SendDeclarePublisher(request, replyTo) =>
          val nextId = correlationId + 1
          val buffer = DeclarePublisherCodec.encode(request, nextId)
          connection.sendFrame(buffer)
          connected(connection, nextId, pending + (nextId -> replyTo))
        case SendPublish(request) =>
          val buffer = PublishCodec.encode(request)
          connection.sendFrame(buffer)
          Behaviors.same
        case Close =>
          connection.close()
          Behaviors.stopped
        case FrameReceived(key, version, buffer) =>
          println(s"Received frame: key=0x${key.toHexString}, version=$version")
          key match {
            case Protocol.Commands.CreateResponse =>
              CreateCodec.decode(buffer, key, version) match {
                case Right(response) =>
                  pending.get(response.correlationId) match {
                    case Some(actor) =>
                      actor.asInstanceOf[ActorRef[CreateResponse]] ! response
                    case None =>
                      println(
                        "Received CreateResponse with unknown correlation ID: {}",
                        response.correlationId
                      )
                  }
                  connected(
                    connection,
                    correlationId,
                    pending - response.correlationId
                  )
                case Left(error) =>
                  println(
                    "Failed to decode CreateResponse: {}",
                    error
                  )
                  Behaviors.same
              }
            case Protocol.Commands.DeclarePublisherResponse =>
              DeclarePublisherCodec.decode(buffer, key, version) match {
                case Right(response) =>
                  pending.get(response.correlationId) match {
                    case Some(actor) =>
                      actor.asInstanceOf[
                        ActorRef[DeclarePublisherResponse]
                      ] ! response
                    case None =>
                      println(
                        "Received DeclarePublisherResponse with unknown correlation ID: {}",
                        response.correlationId
                      )
                  }
                  connected(
                    connection,
                    correlationId,
                    pending - response.correlationId
                  )
                case Left(error) =>
                  println(
                    "Failed to decode DeclarePublisherResponse: {}",
                    error
                  )
                  Behaviors.same
              }
            case Protocol.Commands.PublishConfirm =>
              PublishConfirmCodec.decode(buffer, key, version) match {
                case Right(confirm) =>
                  // TODO: Route to PublisherActor when we have publishers map
                  println(
                    s"PublishConfirm: publisherId=${confirm.publisherId}, publishingIds=${confirm.publishingIds}"
                  )
                  Behaviors.same
                case Left(error) =>
                  println(s"Failed to decode PublishConfirm: $error")
                  Behaviors.same
              }

            case Protocol.Commands.PublishError =>
              PublishErrorCodec.decode(buffer, key, version) match {
                case Right(error) =>
                  println(
                    s"PublishError: publisherId=${error.publisherId}, errors=${error.publishingErrors}"
                  )
                  Behaviors.same
                case Left(err) =>
                  println(s"Failed to decode PublishError: $err")
                  Behaviors.same
              }
            case _ =>
              println("Unknown frame type: key=0x{}", key.toHexString)
              Behaviors.same
          }
        case _ =>
          Behaviors.unhandled
      }
      .receiveSignal { case (context, org.apache.pekko.actor.typed.PostStop) =>
        scala.util.Try(connection.close())
        Behaviors.same
      }
}
