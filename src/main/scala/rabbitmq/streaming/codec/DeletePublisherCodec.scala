package rabbitmq.streaming

import java.nio.ByteBuffer

object DeletePublisherCodec {
  def encode(request: DeletePublisherRequest, correlationId: Int): ByteBuffer = {
    val totalSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId +
      Protocol.Sizes.PublisherId

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.DeletePublisher)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    buffer.put(request.publisherId)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, DeletePublisherResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.DeletePublisherResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
    } yield DeletePublisherResponse(correlationId, responseCode)
  }
}