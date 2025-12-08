package rabbitmq.streaming

import java.nio.ByteBuffer

object ConsumerUpdateCodec {
  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, ConsumerUpdate] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.ConsumerUpdate,
        (),
        s"Invalid key field"
      )
      subscriptionId = buffer.get()
      active = buffer.get() == 1
    } yield ConsumerUpdate(subscriptionId, active)
  }
}