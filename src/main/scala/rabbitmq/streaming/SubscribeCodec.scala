package rabbitmq.streaming

import java.nio.ByteBuffer

object SubscribeCodec {

  def encode(request: SubscribeRequest, correlationId: Int): ByteBuffer = {
    // Calculate size
    val fixedSize = Protocol.Sizes.Key + Protocol.Sizes.Version + 
                   Protocol.Sizes.CorrelationId + Protocol.Sizes.SubscriptionId +
                   Protocol.Sizes.StringLength + request.stream.getBytes("UTF-8").length +
                   2 + 8 + // offset type (2) + offset value (8)  
                   2 + // credit
                   4 // properties array length
    
    val propertiesSize = request.properties.map { case (k, v) =>
      Protocol.Sizes.StringLength + k.getBytes("UTF-8").length +
      Protocol.Sizes.StringLength + v.getBytes("UTF-8").length
    }.sum
    
    val totalSize = fixedSize + propertiesSize
    val buffer = Protocol.allocate(totalSize)

    // Write header
    buffer.putShort(Protocol.Commands.Subscribe)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    buffer.put(request.subscriptionId)
    
    // Write stream
    Protocol.writeString(buffer, request.stream)
    
    // Write offset specification
    request.offsetSpecification match {
      case OffsetSpecification.First => 
        buffer.putShort(1)
        buffer.putLong(0L)
      case OffsetSpecification.Last =>
        buffer.putShort(2) 
        buffer.putLong(0L)
      case OffsetSpecification.Next =>
        buffer.putShort(3)
        buffer.putLong(0L)
      case OffsetSpecification.Offset(value) =>
        buffer.putShort(4)
        buffer.putLong(value)
      case OffsetSpecification.Timestamp(value) =>
        buffer.putShort(5)
        buffer.putLong(value)
    }
    
    // Write credit
    buffer.putShort(request.credit)
    
    // Write properties
    buffer.putInt(request.properties.size)
    request.properties.foreach { case (key, value) =>
      Protocol.writeString(buffer, key)
      Protocol.writeString(buffer, value)
    }

    buffer
  }

  def decode(buffer: ByteBuffer, expectedKey: Short, expectedVersion: Short): Either[String, SubscribeResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.SubscribeResponse,
        (),
        s"Invalid key field"
      )
      version <- Either.cond(
        expectedVersion == Protocol.ProtocolVersion,
        (),
        s"Incompatible protocol version"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
    } yield SubscribeResponse(correlationId, responseCode)
  }
}