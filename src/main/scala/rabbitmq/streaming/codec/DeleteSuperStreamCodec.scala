package rabbitmq.streaming

import java.nio.ByteBuffer

object DeleteSuperStreamCodec {
  def encode(request: DeleteSuperStreamRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId

    val nameBytes = request.name.getBytes("UTF-8")
    val nameSize = Protocol.Sizes.StringLength + nameBytes.length

    val totalSize = fixedSize + nameSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.DeleteSuperStream)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.name)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, DeleteSuperStreamResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.DeleteSuperStreamResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
    } yield DeleteSuperStreamResponse(correlationId, responseCode)
  }
}