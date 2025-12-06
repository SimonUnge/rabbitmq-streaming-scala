package rabbitmq.streaming

import java.nio.ByteBuffer

object DeclarePublisherCodec {

  def encode(
      request: DeclarePublisherRequest,
      correlationId: Int
  ): ByteBuffer = {
    // Estimate size: 1 (publisherId) + 2 + stream.length + 2 + publisherReference.length (if present)
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId +
      Protocol.Sizes.PublisherId

    val streamBytes = request.stream.getBytes("UTF-8")
    val streamSize = Protocol.Sizes.StringLength + streamBytes.length
    val publisherRefSize = request.publisherReference match {
      case Some(ref) =>
        Protocol.Sizes.StringLength + ref.getBytes("UTF-8").length
      case None =>
        Protocol.Sizes.StringLength // just the length indicator
    }
    val totalSize = fixedSize + streamSize + publisherRefSize
    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.DeclarePublisher)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    buffer.put(request.publisherId)
    Protocol.writeOptionalString(buffer, request.publisherReference)
    Protocol.writeString(buffer, request.stream)
    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      expectedVersion: Short
  ): Either[String, DeclarePublisherResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.DeclarePublisherResponse,
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
    } yield DeclarePublisherResponse(correlationId.toInt, responseCode)
  }
}
