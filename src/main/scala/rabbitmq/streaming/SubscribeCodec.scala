package rabbitmq.streaming

import java.nio.ByteBuffer

object SubscribeCodec {
  def encode(request: SubscribeRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId +
      Protocol.Sizes.SubscriptionId +
      Protocol.Sizes.OffsetType +
      Protocol.Sizes.Offset +
      Protocol.Sizes.Credit +
      Protocol.Sizes.ArrayLength

    val streamBytes = request.stream.getBytes("UTF-8")
    val streamSize = Protocol.Sizes.StringLength + streamBytes.length

    val propertiesSize = request.properties.map { case (key, value) =>
      Protocol.Sizes.StringLength + key.getBytes("UTF-8").length +
        Protocol.Sizes.StringLength + value.getBytes("UTF-8").length
    }.sum

    val totalSize = fixedSize + streamSize + propertiesSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Subscribe)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    buffer.put(request.subscriptionId)
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
      case OffsetSpecification.Offset(offset) =>
        buffer.putShort(4)
        buffer.putLong(offset)
      case OffsetSpecification.Timestamp(timestamp) =>
        buffer.putShort(5)
        buffer.putLong(timestamp)
    }

    buffer.putShort(request.credit)
    buffer.putInt(request.properties.size)
    request.properties.foreach { case (key, value) =>
      Protocol.writeString(buffer, key)
      Protocol.writeString(buffer, value)
    }

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      expectedVersion: Short
  ): Either[String, SubscribeResponse] = {
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
    } yield SubscribeResponse(correlationId.toInt, responseCode)
  }
}
