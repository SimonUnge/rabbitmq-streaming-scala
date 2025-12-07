package rabbitmq.streaming

import java.nio.ByteBuffer

object PartitionsCodec {
  def encode(request: PartitionsRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId

    val superStreamBytes = request.superStream.getBytes("UTF-8")
    val superStreamSize = Protocol.Sizes.StringLength + superStreamBytes.length

    val totalSize = fixedSize + superStreamSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Partitions)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.superStream)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, PartitionsResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.PartitionsResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
      streamCount = buffer.getInt()
      streams <- Right {
        (0 until streamCount).map(_ => Protocol.readString(buffer)).toList
      }
    } yield PartitionsResponse(correlationId, responseCode, streams)
  }
}