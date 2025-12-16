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
  case class SendPublish(request: PublishRequest)         extends Command // Fire-and-forget
  case class RegisterPublisher(
      id: Byte,
      actor: ActorRef[PublisherActor.Command]
  ) extends Command
  case class RegisterSubscriber(
      id: Byte,
      actor: ActorRef[SubscriberActor.Command]
  ) extends Command
  case class SendSubscribe(
      request: SubscribeRequest,
      replyTo: ActorRef[SubscribeResponse]
  ) extends Command
  case class SendUnsubscribe(request: UnsubscribeRequest) extends Command // Fire-and-forget
  case class SendCredit(request: CreditRequest)           extends Command

  // TODO: Add DeregisterPublisher command
  // TODO: Add DeregisterSubscriber command
  // TODO: Add SendDeletePublisher command (optional)

  sealed trait SendResult
  case object FrameSent                extends SendResult
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
        }
        //   - subscribers = Map.empty (for later)
        connected(connection, 0, Map.empty, Map.empty, Map.empty)
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
        "product"  -> "rabbitmq-streaming-scala",
        "version"  -> "0.1.0",
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
    val saslData    =
      s"\u0000${config.username}\u0000${config.password}".getBytes("UTF-8")
    val saslAuthReq = SaslAuthenticateRequest("PLAIN", saslData)
    connection.sendFrame(
      SaslAuthenticateCodec.encode(saslAuthReq, correlationId)
    )
    val authResp    =
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
    val openReq                    = OpenRequest("/")
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
      pending: Map[Int, ActorRef[_]],
      publishers: Map[Byte, ActorRef[PublisherActor.Command]],
      subscribers: Map[Byte, ActorRef[SubscriberActor.Command]]
  ): Behavior[Command] =
    Behaviors
      .receive[Command] { (context, message) =>
        message match {
          case SendCreate(request, replyTo)           =>
            val nextId = correlationId + 1
            val buffer = CreateCodec.encode(request, nextId)
            connection.sendFrame(buffer)
            connected(
              connection,
              nextId,
              pending + (nextId -> replyTo),
              publishers,
              subscribers
            )
          case SendDeclarePublisher(request, replyTo) =>
            val nextId = correlationId + 1
            val buffer = DeclarePublisherCodec.encode(request, nextId)
            connection.sendFrame(buffer)
            connected(
              connection,
              nextId,
              pending + (nextId -> replyTo),
              publishers,
              subscribers
            )
          case SendPublish(request)                   =>
            val buffer = PublishCodec.encode(request)
            connection.sendFrame(buffer)
            Behaviors.same
          case RegisterPublisher(id, replyTo)         =>
            connected(
              connection,
              correlationId,
              pending,
              publishers + (id -> replyTo),
              subscribers
            )
          case RegisterSubscriber(id, replyTo)        =>
            connected(
              connection,
              correlationId,
              pending,
              publishers,
              subscribers + (id -> replyTo)
            )
          case SendSubscribe(request, replyTo)        =>
            val nextId = correlationId + 1
            val buffer = SubscribeCodec.encode(request, nextId)
            connection.sendFrame(buffer)
            connected(
              connection,
              nextId,
              pending + (nextId -> replyTo),
              publishers,
              subscribers
            )
          case SendCredit(request)                    =>
            val buffer = CreditCodec.encode(request)
            connection.sendFrame(buffer)
            Behaviors.same
          case SendUnsubscribe(request)               =>
            val nextId = correlationId + 1
            val buffer = UnsubscribeCodec.encode(request, nextId)
            connection.sendFrame(buffer)
            connected(
              connection,
              nextId,
              pending,
              publishers,
              subscribers - request.subscriptionId
            )
          case Close                                  =>
            connection.close()
            Behaviors.stopped
          case FrameReceived(key, version, buffer)    =>
            context.log.debug(
              "Received frame: key=0x{}, version={}",
              key.toHexString,
              version
            )
            key match {
              case Protocol.Commands.CreateResponse           =>
                CreateCodec.decode(buffer, key, version) match {
                  case Right(response) =>
                    pending.get(response.correlationId) match {
                      case Some(actor) =>
                        actor.asInstanceOf[ActorRef[CreateResponse]] ! response
                      case None        =>
                        context.log.warn(
                          "Received CreateResponse with unknown correlation ID: {}",
                          response.correlationId
                        )
                    }
                    connected(
                      connection,
                      correlationId,
                      pending - response.correlationId,
                      publishers,
                      subscribers
                    )
                  case Left(error)     =>
                    context.log.error(
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
                      case None        =>
                        context.log.warn(
                          "Received DeclarePublisherResponse with unknown correlation ID: {}",
                          response.correlationId
                        )
                    }
                    connected(
                      connection,
                      correlationId,
                      pending - response.correlationId,
                      publishers,
                      subscribers
                    )
                  case Left(error)     =>
                    context.log.error(
                      "Failed to decode DeclarePublisherResponse: {}",
                      error
                    )
                    Behaviors.same
                }
              case Protocol.Commands.PublishConfirm           =>
                PublishConfirmCodec.decode(buffer, key, version) match {
                  case Right(confirm) =>
                    publishers.get(confirm.publisherId) match {
                      case Some(actor) =>
                        actor ! PublisherActor.PublishConfirmReceived(
                          confirm.publishingIds
                        )
                      case None        =>
                        context.log.warn(
                          "No publisher registered for ID: {}",
                          confirm.publisherId
                        )
                    }
                    Behaviors.same
                  case Left(error)    =>
                    context.log.error(
                      "Failed to decode PublishConfirm: {}",
                      error
                    )
                    Behaviors.same
                }

              case Protocol.Commands.PublishError        =>
                PublishErrorCodec.decode(buffer, key, version) match {
                  case Right(error) =>
                    publishers.get(error.publisherId) match {
                      case Some(actor) =>
                        actor ! PublisherActor.PublishErrorReceived(
                          error.publishingErrors
                        )
                      case None        =>
                        context.log.warn(
                          "No publisher registered for ID: {}",
                          error.publisherId
                        )
                    }
                    Behaviors.same
                  case Left(err)    =>
                    context.log.error("Failed to decode PublishError: {}", err)
                    Behaviors.same
                }
              case Protocol.Commands.SubscribeResponse   =>
                SubscribeCodec.decode(buffer, key, version) match {
                  case Right(response) =>
                    pending.get(response.correlationId) match {
                      case Some(actor) =>
                        actor.asInstanceOf[
                          ActorRef[SubscribeResponse]
                        ] ! response
                      case None        =>
                        context.log.warn(
                          "Received SubscribeResponse with unknown correlation ID: {}",
                          response.correlationId
                        )
                    }
                    connected(
                      connection,
                      correlationId,
                      pending - response.correlationId,
                      publishers,
                      subscribers
                    )
                  case Left(error)     =>
                    context.log.error(
                      "Failed to decode DeclarePublisherResponse: {}",
                      error
                    )
                    Behaviors.same
                }
              case Protocol.Commands.UnsubscribeResponse =>
                Behaviors.same

              // TODO: Add Deliver message routing to subscribers
              // case Protocol.Commands.Deliver =>
              //   DeliverCodec.decode(buffer, key, version) match {
              //     case Right(deliver) =>
              //       subscribers.get(deliver.subscriptionId) match {
              //         case Some(actor) =>
              //           actor ! SubscriberActor.DeliverReceived(deliver.chunk)
              //         case None =>
              //           context.log.warn("No subscriber for ID: {}", deliver.subscriptionId)
              //       }
              //       Behaviors.same
              //   }

              case Protocol.Commands.Heartbeat =>
                val heartbeat = HeartbeatCodec.encode()
                connection.sendFrame(heartbeat)
                Behaviors.same
              case _                           =>
                context.log.warn(
                  "Unknown frame type: key=0x{}",
                  key.toHexString
                )
                Behaviors.same
            }
          case _                                      =>
            Behaviors.unhandled
        }
      }
      .receiveSignal { case (context, org.apache.pekko.actor.typed.PostStop) =>
        context.log.info("ConnectionActor stopping")
        scala.util.Try(connection.close())
        Behaviors.same
      }
}
