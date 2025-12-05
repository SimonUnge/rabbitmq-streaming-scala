package rabbitmq.streaming

import java.nio.ByteBuffer

object PeerPropertiesCodec {

  def encode(request: PeerPropertiesRequest, correlationId: Int): ByteBuffer = {
    // Estimate size: 2 (key) + 2 (version) + 4 (correlationId) + properties size
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId +
      Protocol.Sizes.ArrayLength

    val propertiesSize = request.properties.map { case (key, value) =>
      Protocol.Sizes.StringLength + key.getBytes("UTF-8").length +
        Protocol.Sizes.StringLength + value.getBytes("UTF-8").length
    }.sum

    val totalSize = fixedSize + propertiesSize
    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.PeerProperties)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
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
  ): Either[String, PeerPropertiesResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.PeerPropertiesResponse,
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
      numProperties = buffer.getInt()
      properties <- Right {
        (0 until numProperties).map { _ =>
          val propKey = Protocol.readString(buffer)
          val propValue = Protocol.readString(buffer)
          (propKey, propValue)
        }.toMap
      }
    } yield PeerPropertiesResponse(correlationId, responseCode, properties)
  }
}
