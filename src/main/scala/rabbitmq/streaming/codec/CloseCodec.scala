package rabbitmq.streaming

import java.nio.ByteBuffer

object CloseCodec {
  def encode(request: CloseRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId

    val reasonBytes = request.reason.getBytes("UTF-8")
    val reasonSize = Protocol.Sizes.StringLength + reasonBytes.length

    val totalSize = fixedSize + reasonSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Close)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.reason)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, CloseResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.CloseResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
    } yield CloseResponse(correlationId, responseCode)
  }
}