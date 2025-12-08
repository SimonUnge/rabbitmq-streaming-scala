package rabbitmq.streaming

import java.nio.ByteBuffer

object QueryOffsetCodec {
  def encode(request: QueryOffsetRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId

    val referenceBytes = request.reference.getBytes("UTF-8")
    val referenceSize = Protocol.Sizes.StringLength + referenceBytes.length

    val streamBytes = request.stream.getBytes("UTF-8")
    val streamSize = Protocol.Sizes.StringLength + streamBytes.length

    val totalSize = fixedSize + referenceSize + streamSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.QueryOffset)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.reference)
    Protocol.writeString(buffer, request.stream)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, QueryOffsetResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.QueryOffsetResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
      offset = buffer.getLong()
    } yield QueryOffsetResponse(correlationId, responseCode, offset)
  }
}