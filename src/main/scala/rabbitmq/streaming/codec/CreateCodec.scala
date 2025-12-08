package rabbitmq.streaming

import java.nio.ByteBuffer

object CreateCodec {
  def encode(request: CreateRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key + 
      Protocol.Sizes.Version + 
      Protocol.Sizes.CorrelationId

    val streamBytes = request.stream.getBytes("UTF-8")
    val streamSize = Protocol.Sizes.StringLength + streamBytes.length

    val argumentSize = Protocol.Sizes.ArrayLength + request.arguments.map { case (key, value) =>
      Protocol.Sizes.StringLength + key.getBytes("UTF-8").length + 
      Protocol.Sizes.StringLength + value.getBytes("UTF-8").length
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
      version: Short
  ): Either[String, CreateResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.CreateResponse,
        (),
        s"Invalid key field"
      )
      // Version parameter received but not used for this simple response
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
    } yield CreateResponse(correlationId.toInt, responseCode)
  }
}
