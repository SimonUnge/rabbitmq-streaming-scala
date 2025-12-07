package rabbitmq.streaming

import java.nio.ByteBuffer

object MetadataCodec {
  def encode(request: MetadataRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId +
      Protocol.Sizes.ArrayLength

    val streamsSize = request.streams.map { stream =>
      Protocol.Sizes.StringLength + stream.getBytes("UTF-8").length
    }.sum

    val totalSize = fixedSize + streamsSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Metadata)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    buffer.putInt(request.streams.size)
    request.streams.foreach { stream =>
      Protocol.writeString(buffer, stream)
    }

    buffer
  }

  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, MetadataResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.MetadataResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
      brokerCount = buffer.getInt()
      brokers <- Right {
        (0 until brokerCount).map { _ =>
          val reference = buffer.getShort()
          val host = Protocol.readString(buffer)
          val port = buffer.getInt()
          Broker(reference, host, port)
        }.toList
      }
      streamCount = buffer.getInt()
      streamMetadata <- Right {
        (0 until streamCount).map { _ =>
          val streamName = Protocol.readString(buffer)
          val streamResponseCode = buffer.getShort()
          val leaderReference = buffer.getShort()
          val replicaCount = buffer.getInt()
          val replicaReferences = (0 until replicaCount).map(_ => buffer.getShort()).toList
          StreamMetadata(streamName, streamResponseCode, leaderReference, replicaReferences)
        }.toList
      }
    } yield MetadataResponse(correlationId, responseCode, brokers, streamMetadata)
  }
}