package rabbitmq.streaming

import java.nio.ByteBuffer

object SaslAuthenticateCodec {

  def encode(
      request: SaslAuthenticateRequest,
      correlationId: Int
  ): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId

    val mechanismBytes = request.mechanism.getBytes("UTF-8")
    val mechanismSize = Protocol.Sizes.StringLength + mechanismBytes.length

    val saslDataSize =
      Protocol.Sizes.ArrayLength + request.saslOpaqueData.length

    val totalSize = fixedSize + mechanismSize + saslDataSize

    val buffer = Protocol.allocate(totalSize)
    buffer.putShort(Protocol.Commands.SaslAuthenticate)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)

    Protocol.writeString(buffer, request.mechanism)
    Protocol.writeBytes(buffer, request.saslOpaqueData)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      expectedVersion: Short
  ): Either[String, SaslAuthenticateResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.SaslAuthenticateResponse,
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
      saslOpaqueData = Protocol.readOptionalBytes(buffer)
    } yield SaslAuthenticateResponse(
      correlationId,
      responseCode,
      saslOpaqueData
    )
  }
}
