package rabbitmq.streaming

import java.nio.ByteBuffer

object PublishErrorCodec {
  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, PublishError] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.PublishError,
        (),
        s"Invalid key field"
      )
      publisherId = buffer.get()
      count = buffer.getInt()
      publishingErrors <- Right {
        (0 until count).map { _ =>
          val publishingId = buffer.getLong()
          val errorCode = buffer.getShort()
          PublishingError(publishingId, errorCode)
        }.toList
      }
    } yield PublishError(publisherId, publishingErrors)
  }
}