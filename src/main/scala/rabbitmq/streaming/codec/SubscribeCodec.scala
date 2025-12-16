package rabbitmq.streaming

import java.nio.ByteBuffer

object SubscribeCodec {
  def encode(request: SubscribeRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId +
      Protocol.Sizes.SubscriptionId +
      Protocol.Sizes.OffsetType +
      Protocol.Sizes.Credit

    val offsetSize = request.offsetSpecification match {
      case OffsetSpecification.Offset(_) | OffsetSpecification.Timestamp(_) => 8
      case _                                                                => 0
    }

    val streamBytes = request.stream.getBytes("UTF-8")
    val streamSize = Protocol.Sizes.StringLength + streamBytes.length

    val propertiesSize = if (request.properties.nonEmpty) {
      Protocol.Sizes.ArrayLength + request.properties.map { case (key, value) =>
        Protocol.Sizes.StringLength + key.getBytes("UTF-8").length +
          Protocol.Sizes.StringLength + value.getBytes("UTF-8").length
      }.sum
    } else {
      0
    }

    val totalSize = fixedSize + offsetSize + streamSize + propertiesSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Subscribe)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    buffer.put(request.subscriptionId)
    Protocol.writeString(buffer, request.stream)
    request.offsetSpecification match {
      case OffsetSpecification.First =>
        buffer.putShort(1)
      case OffsetSpecification.Last =>
        buffer.putShort(2)
      case OffsetSpecification.Next =>
        buffer.putShort(3)
      case OffsetSpecification.Offset(offset) =>
        buffer.putShort(4)
        buffer.putLong(offset)
      case OffsetSpecification.Timestamp(timestamp) =>
        buffer.putShort(5)
        buffer.putLong(timestamp)
    }

    buffer.putShort(request.credit)

    if (request.properties.nonEmpty) {
      buffer.putInt(request.properties.size)
      request.properties.foreach { case (key, value) =>
        Protocol.writeString(buffer, key)
        Protocol.writeString(buffer, value)
      }
    }

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, SubscribeResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.SubscribeResponse,
        (),
        s"Invalid key field"
      )
      // Version parameter received but not used for this simple response
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
    } yield SubscribeResponse(correlationId.toInt, responseCode)
  }
}
