package rabbitmq.streaming

import java.nio.ByteBuffer

object CreateCodec {
  def encode(request: CreateRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = 2 + // Key
      2 + // Version
      4 // CorrelationId

    val streamBytes = request.stream.getBytes("UTF-8")
    val streamSize = 2 + streamBytes.length

    val argumentSize = 4 + request.arguments.map { case (key, value) =>
      2 + key.getBytes("UTF-8").length + 2 + value.getBytes("UTF-8").length
    }.sum

    val totalSize = fixedSize + streamSize + argumentSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Create)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.stream)
    buffer.putInt(request.arguments.size)

    request.arguments.foreach { case (key, value) =>
      Protocol.writeString(buffer, key)
      Protocol.writeString(buffer, value)
    }
    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      expectedVersion: Short
  ): Either[String, CreateResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.CreateResponse,
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
    } yield CreateResponse(correlationId.toInt, responseCode)
  }
}
