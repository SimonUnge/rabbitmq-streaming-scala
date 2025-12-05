package rabbitmq.streaming

import java.nio.ByteBuffer

object SaslHandshakeCodec {

  def encode(correlationId: Int): ByteBuffer = {
    // Fixed size: 2 (key) + 2 (version) + 4 (correlationId)
    val totalSize = 2 + // Key
      2 + // Version
      4 // CorrelationId

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.SaslHandshake)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      expectedVersion: Short
  ): Either[String, SaslHandshakeResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.SaslHandshakeResponse,
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
      numMechanisms = buffer.getInt()
      mechanisms <- Right {
        (0 until numMechanisms).map { _ =>
          Protocol.readString(buffer)
        }.toList
      }
    } yield SaslHandshakeResponse(correlationId, responseCode, mechanisms)
  }
}
