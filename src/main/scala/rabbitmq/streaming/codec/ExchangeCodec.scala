package rabbitmq.streaming

import java.nio.ByteBuffer

object ExchangeCodec {
  def encode(request: ExchangeRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId

    val exchangeBytes = request.exchange.getBytes("UTF-8")
    val exchangeSize = Protocol.Sizes.StringLength + exchangeBytes.length

    val routingKeyBytes = request.routingKey.getBytes("UTF-8")
    val routingKeySize = Protocol.Sizes.StringLength + routingKeyBytes.length

    val messageSize = Protocol.Sizes.ArrayLength + request.message.length

    val totalSize = fixedSize + exchangeSize + routingKeySize + messageSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Exchange)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.exchange)
    Protocol.writeString(buffer, request.routingKey)
    Protocol.writeBytes(buffer, request.message)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, ExchangeResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.ExchangeResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
    } yield ExchangeResponse(correlationId, responseCode)
  }
}