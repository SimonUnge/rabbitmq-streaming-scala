package rabbitmq.streaming

import java.nio.ByteBuffer

object PublishCodec {

  def encode(request: PublishRequest): ByteBuffer = {
    val fixedSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.PublisherId +
      Protocol.Sizes.ArrayLength

    val publishedMessagesSize = request.publishedMessages.map {
      publishedMessage =>
        Protocol.Sizes.Offset + Protocol.Sizes.ArrayLength + publishedMessage.message.length +
          publishedMessage.filterValue
            .map(filter =>
              Protocol.Sizes.StringLength + filter.getBytes("UTF-8").length
            )
            .getOrElse(0)
    }.sum

    val totalSize = fixedSize + publishedMessagesSize
    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Publish)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.put(request.publisherId)
    buffer.putInt(request.publishedMessages.size)

    request.publishedMessages.foreach { publishedMessage =>
      buffer.putLong(publishedMessage.publishingId)
      publishedMessage.filterValue.foreach { filter =>
        Protocol.writeString(buffer, filter)
      }
      Protocol.writeBytes(buffer, publishedMessage.message)
    }

    buffer
  }
}
