package rabbitmq.streaming

import java.nio.ByteBuffer

object QueryPublisherSequenceCodec {
  def encode(request: QueryPublisherSequenceRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId

    val referenceBytes = request.publisherReference.getBytes("UTF-8")
    val referenceSize = Protocol.Sizes.StringLength + referenceBytes.length

    val streamBytes = request.stream.getBytes("UTF-8")
    val streamSize = Protocol.Sizes.StringLength + streamBytes.length

    val totalSize = fixedSize + referenceSize + streamSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.QueryPublisherSequence)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.publisherReference)
    Protocol.writeString(buffer, request.stream)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, QueryPublisherSequenceResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.QueryPublisherSequenceResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
      sequence = buffer.getLong()
    } yield QueryPublisherSequenceResponse(correlationId, responseCode, sequence)
  }
}