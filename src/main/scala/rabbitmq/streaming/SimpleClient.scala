package rabbitmq.streaming
import java.nio.ByteBuffer

object SimpleClient {
  def main(args: Array[String]): Unit = {
    val config = ConnectionConfig(
      host = sys.env.getOrElse("RABBITMQ_HOST", "localhost"),
      port = sys.env.get("RABBITMQ_PORT").map(_.toInt).getOrElse(5552),
      username = sys.env.getOrElse("RABBITMQ_USERNAME", "guest"),
      password = sys.env.getOrElse("RABBITMQ_PASSWORD", "guest")
    )

    val connection = new Connection(config)
    var correlationId = 0

    def receiveAndDecode[T](
        codec: (ByteBuffer, Short, Short) => Either[String, T]
    ): Either[String, T] = {
      val (key, version, buffer) = connection.receiveFrame()
      codec(buffer, key, version)
    }

    try {
      println("Connecting to RabbitMQ...")

      // 1. Peer Properties
      println("\n Sending Peer Properties...")
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
      receiveAndDecode(PeerPropertiesCodec.decode) match {
        case Right(resp) => println(s"Server properties: ${resp.properties}")
        case Left(err)   => throw new Exception(s"Peer Properties failed: $err")
      }

      // 2. SASL Handshake
      println("\nSASL Handshake...")
      correlationId += 1
      connection.sendFrame(SaslHandshakeCodec.encode(correlationId))
      val mechanisms = receiveAndDecode(SaslHandshakeCodec.decode) match {
        case Right(resp) =>
          println(s"Available mechanisms: ${resp.mechanisms}")
          resp.mechanisms
        case Left(err) => throw new Exception(s"SASL Handshake failed: $err")
      }

      // 3. SASL Authenticate
      println("\nSASL Authenticate (PLAIN)...")
      correlationId += 1
      val saslData =
        s"\u0000${config.username}\u0000${config.password}".getBytes("UTF-8")
      val saslAuthReq = SaslAuthenticateRequest("PLAIN", saslData)
      connection.sendFrame(
        SaslAuthenticateCodec.encode(saslAuthReq, correlationId)
      )
      receiveAndDecode(SaslAuthenticateCodec.decode) match {
        case Right(resp) if resp.responseCode == Protocol.ResponseCodes.OK =>
          println(s"Authentication successful!")
        case Right(resp) =>
          throw new Exception(
            s"Authentication failed with code: ${resp.responseCode}"
          )
        case Left(err) => throw new Exception(s"SASL Authenticate failed: $err")
      }

      // 4. Tune
      println("\nTune negotiation...")
      receiveAndDecode(TuneCodec.decode) match {
        case Right(tune) =>
          println(
            s"Server tune: frameMax=${tune.frameMax}, heartbeat=${tune.heartbeat}"
          )
          // Accept server's values
          connection.sendFrame(TuneCodec.encode(tune))
          println(s"Sent tune response")
        case Left(err) => throw new Exception(s"Tune failed: $err")
      }

      // 5. Open
      println("\nOpening virtual host...")
      correlationId += 1
      val openReq = OpenRequest("/")
      connection.sendFrame(OpenCodec.encode(openReq, correlationId))
      receiveAndDecode(OpenCodec.decode) match {
        case Right(resp) if resp.responseCode == Protocol.ResponseCodes.OK =>
          println(s"Virtual host opened!")
          println(s"Connection properties: ${resp.connectionProperties}")
        case Right(resp) =>
          throw new Exception(s"Open failed with code: ${resp.responseCode}")
        case Left(err) => throw new Exception(s"Open failed: $err")
      }

      println("\nSuccessfully connected to RabbitMQ!")

      // 6. Create a stream
      println("\nCreating a stream...")
      correlationId += 1
      val streamName = "scalastream"
      val createReq = CreateRequest(streamName, Map.empty)
      connection.sendFrame(CreateCodec.encode(createReq, correlationId))
      receiveAndDecode(CreateCodec.decode) match {
        case Right(resp) if resp.responseCode == Protocol.ResponseCodes.OK =>
          println(s"Stream created!")
        case Right(resp)
            if resp.responseCode == Protocol.ResponseCodes.StreamAlreadyExists =>
          println(s"Stream Already Exists!")
        case Right(resp) =>
          throw new Exception(s"Create failed with code: ${resp.responseCode}")
        case Left(err) => throw new Exception(s"Create failed: $err")
      }

      // 7. Declare Publisher
      println("\nDeclaring publisher...")
      correlationId += 1
      val publisherId: Byte = 1
      val declareReq =
        DeclarePublisherRequest(publisherId, streamName)

      connection.sendFrame(
        DeclarePublisherCodec.encode(declareReq, correlationId)
      )
      receiveAndDecode(DeclarePublisherCodec.decode) match {
        case Right(resp) if resp.responseCode == Protocol.ResponseCodes.OK =>
          println("Publisher declared successfully!")
        case Right(resp) =>
          throw new Exception(
            s"Declare publisher failed with code: ${resp.responseCode}"
          )
        case Left(err) => throw new Exception(s"Declare publisher failed: $err")
      }

// 8. Publish Messages
      println("\nPublishing messages...")
      val messages = List(
        PublishedMessage(1L, "Hello from Scala!".getBytes("UTF-8")),
        PublishedMessage(2L, "Second message".getBytes("UTF-8")),
        PublishedMessage(3L, "Third message".getBytes("UTF-8"))
      )
      val publishReq = PublishRequest(publisherId, messages)
      connection.sendFrame(PublishCodec.encode(publishReq))
      println(s"Published ${messages.size} messages (fire-and-forget)")

    } catch {
      case e: Exception =>
        println(s"\nError: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      connection.close()
      println("\nConnection closed")
    }
  }
}
