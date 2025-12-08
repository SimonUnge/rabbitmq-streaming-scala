package rabbitmq.streaming

import java.nio.ByteBuffer

object OpenCodec {

  def encode(request: OpenRequest, correlationId: Int): ByteBuffer = {
    // Estimate size: 2 (key) + 2 (version) + 4 (correlationId) + properties size
    val fixedSize = Protocol.Sizes.Key + 
      Protocol.Sizes.Version + 
      Protocol.Sizes.CorrelationId

    val virtualHostBytes = request.virtualHost.getBytes("UTF-8")
    val virtualHostSize = Protocol.Sizes.StringLength + virtualHostBytes.length

    val totalSize = fixedSize + virtualHostSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Open)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.virtualHost)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, OpenResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.OpenResponse,
        (),
        s"Invalid key field"
      )
      // Version parameter received but not used for this simple response
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
      numConnectionProperties = buffer.getInt()
      connectionProperties <- Right {
        (0 until numConnectionProperties).map { _ =>
          val propKey = Protocol.readString(buffer)
          val propValue = Protocol.readString(buffer)
          (propKey, propValue)
        }.toMap
      }
    } yield OpenResponse(correlationId, responseCode, connectionProperties)
  }
}
