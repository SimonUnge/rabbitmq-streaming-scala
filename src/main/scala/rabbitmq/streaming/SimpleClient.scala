package rabbitmq.streaming

object SimpleClient {
  def main(args: Array[String]): Unit = {
    val config = ConnectionConfig(
      host = "ec2-3-101-65-153.us-west-1.compute.amazonaws.com",
      port = 5552,
      username = "kingkong",
      password = "godzilla"
    )
    
    val connection = new Connection(config)
    var correlationId = 0
    
    try {
      println("🔌 Connecting to RabbitMQ...")
      
      // 1. Peer Properties
      println("\n1️⃣ Sending Peer Properties...")
      correlationId += 1
      val peerPropsReq = PeerPropertiesRequest(Map(
        "product" -> "rabbitmq-streaming-scala",
        "version" -> "0.1.0",
        "platform" -> "Scala"
      ))
      connection.sendFrame(PeerPropertiesCodec.encode(peerPropsReq, correlationId))
      val (_, _, peerPropsBuffer) = connection.receiveFrame()
      PeerPropertiesCodec.decode(peerPropsBuffer) match {
        case Right(resp) => println(s"✅ Server properties: ${resp.properties}")
        case Left(err) => throw new Exception(s"Peer Properties failed: $err")
      }
      
      // 2. SASL Handshake
      println("\n2️⃣ SASL Handshake...")
      correlationId += 1
      connection.sendFrame(SaslHandshakeCodec.encode(correlationId))
      val (_, _, saslHandshakeBuffer) = connection.receiveFrame()
      val mechanisms = SaslHandshakeCodec.decode(saslHandshakeBuffer) match {
        case Right(resp) => 
          println(s"✅ Available mechanisms: ${resp.mechanisms}")
          resp.mechanisms
        case Left(err) => throw new Exception(s"SASL Handshake failed: $err")
      }
      
      // 3. SASL Authenticate
      println("\n3️⃣ SASL Authenticate (PLAIN)...")
      correlationId += 1
      val saslData = s"\u0000${config.username}\u0000${config.password}".getBytes("UTF-8")
      val saslAuthReq = SaslAuthenticateRequest("PLAIN", saslData)
      connection.sendFrame(SaslAuthenticateCodec.encode(saslAuthReq, correlationId))
      val (_, _, saslAuthBuffer) = connection.receiveFrame()
      SaslAuthenticateCodec.decode(saslAuthBuffer) match {
        case Right(resp) if resp.responseCode == Protocol.ResponseCodes.OK =>
          println(s"✅ Authentication successful!")
        case Right(resp) =>
          throw new Exception(s"Authentication failed with code: ${resp.responseCode}")
        case Left(err) => throw new Exception(s"SASL Authenticate failed: $err")
      }
      
      // 4. Tune
      println("\n4️⃣ Tune negotiation...")
      val (_, _, tuneBuffer) = connection.receiveFrame()
      TuneCodec.decode(tuneBuffer) match {
        case Right(tune) =>
          println(s"✅ Server tune: frameMax=${tune.frameMax}, heartbeat=${tune.heartbeat}")
          // Accept server's values
          connection.sendFrame(TuneCodec.encode(tune))
          println(s"✅ Sent tune response")
        case Left(err) => throw new Exception(s"Tune failed: $err")
      }
      
      // 5. Open
      println("\n5️⃣ Opening virtual host...")
      correlationId += 1
      val openReq = OpenRequest("/")
      connection.sendFrame(OpenCodec.encode(openReq, correlationId))
      val (_, _, openBuffer) = connection.receiveFrame()
      OpenCodec.decode(openBuffer) match {
        case Right(resp) if resp.responseCode == Protocol.ResponseCodes.OK =>
          println(s"✅ Virtual host opened!")
          println(s"   Connection properties: ${resp.connectionProperties}")
        case Right(resp) =>
          throw new Exception(s"Open failed with code: ${resp.responseCode}")
        case Left(err) => throw new Exception(s"Open failed: $err")
      }
      
      println("\n🎉 Successfully connected to RabbitMQ!")
      
    } catch {
      case e: Exception =>
        println(s"\n❌ Error: ${e.getMessage}")
        e.printStackTrace()
    } finally {
      connection.close()
      println("\n👋 Connection closed")
    }
  }
}