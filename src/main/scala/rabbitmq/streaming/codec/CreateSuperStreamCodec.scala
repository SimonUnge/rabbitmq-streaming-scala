package rabbitmq.streaming

import java.nio.ByteBuffer

object CreateSuperStreamCodec {
  def encode(request: CreateSuperStreamRequest, correlationId: Int): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId +
      Protocol.Sizes.ArrayLength + // partitions count
      Protocol.Sizes.ArrayLength + // binding keys count
      Protocol.Sizes.ArrayLength   // arguments count

    val nameBytes = request.name.getBytes("UTF-8")
    val nameSize = Protocol.Sizes.StringLength + nameBytes.length

    val partitionsSize = request.partitions.map { partition =>
      Protocol.Sizes.StringLength + partition.getBytes("UTF-8").length
    }.sum

    val bindingKeysSize = request.bindingKeys.map { key =>
      Protocol.Sizes.StringLength + key.getBytes("UTF-8").length
    }.sum

    val argumentsSize = request.arguments.map { case (key, value) =>
      Protocol.Sizes.StringLength + key.getBytes("UTF-8").length +
        Protocol.Sizes.StringLength + value.getBytes("UTF-8").length
    }.sum

    val totalSize = fixedSize + nameSize + partitionsSize + bindingKeysSize + argumentsSize

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.CreateSuperStream)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(correlationId)
    Protocol.writeString(buffer, request.name)
    
    buffer.putInt(request.partitions.size)
    request.partitions.foreach { partition =>
      Protocol.writeString(buffer, partition)
    }
    
    buffer.putInt(request.bindingKeys.size)
    request.bindingKeys.foreach { key =>
      Protocol.writeString(buffer, key)
    }
    
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
  ): Either[String, CreateSuperStreamResponse] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.CreateSuperStreamResponse,
        (),
        s"Invalid key field"
      )
      correlationId = buffer.getInt()
      responseCode = buffer.getShort()
    } yield CreateSuperStreamResponse(correlationId, responseCode)
  }
}