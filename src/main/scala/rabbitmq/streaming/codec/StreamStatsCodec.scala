package rabbitmq.streaming

import java.nio.ByteBuffer

object StreamStatsCodec {
  def encode(request: StreamStatsRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId

    val streamBytes = request.stream.getBytes("UTF-8")
    val streamSize = Protocol.Sizes.StringLength + streamBytes.length

    val totalSize = fixedSize + streamSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.StreamStats)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.stream)

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, StreamStatsResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.StreamStatsResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
      statsCount = buffer.getInt()
      stats <- Right {
        (0 until statsCount).map { _ =>
          val key = Protocol.readString(buffer)
          val value = buffer.getLong()
          key -> value
        }.toMap
      }
    } yield StreamStatsResponse(correlationId, responseCode, stats)
  }
}