package rabbitmq.streaming

import java.nio.ByteBuffer

object RouteCodec {
  def encode(request: RouteRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId

    val routingKeyBytes = request.routingKey.getBytes("UTF-8")
    val routingKeySize = Protocol.Sizes.StringLength + routingKeyBytes.length

    val superStreamBytes = request.superStream.getBytes("UTF-8")
    val superStreamSize = Protocol.Sizes.StringLength + superStreamBytes.length

    val totalSize = fixedSize + routingKeySize + superStreamSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Route)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.routingKey)
    Protocol.writeString(buffer, request.superStream)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, RouteResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.RouteResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
      streamCount = buffer.getInt()
      streams <- Right {
        (0 until streamCount).map(_ => Protocol.readString(buffer)).toList
      }
    } yield RouteResponse(correlationId, responseCode, streams)
  }
}