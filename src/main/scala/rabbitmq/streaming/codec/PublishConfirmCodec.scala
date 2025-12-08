package rabbitmq.streaming

import java.nio.ByteBuffer

object PublishConfirmCodec {
  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, PublishConfirm] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.PublishConfirm,
        (),
        s"Invalid key field"
      )
      publisherId = buffer.get()
      count = buffer.getInt()
      publishingIds <- Right {
        (0 until count).map(_ => buffer.getLong()).toList
      }
    } yield PublishConfirm(publisherId, publishingIds)
  }
}