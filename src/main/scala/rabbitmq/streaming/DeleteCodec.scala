package rabbitmq.streaming

import java.nio.ByteBuffer

object DeleteCodec {
  def encode(request: DeleteRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId

    val streamBytes = request.stream.getBytes("UTF-8")
    val streamSize = Protocol.Sizes.StringLength + streamBytes.length

    val totalSize = fixedSize + streamSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Delete)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.stream)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, DeleteResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.DeleteResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
    } yield DeleteResponse(correlationId, responseCode)
  }
}