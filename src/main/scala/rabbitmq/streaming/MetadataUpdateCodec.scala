package rabbitmq.streaming

import java.nio.ByteBuffer

object MetadataUpdateCodec {
  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, MetadataUpdate] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.MetadataUpdate,
        (),
        s"Invalid key field"
      )
      metadataInfo = buffer.getShort()
      stream = Protocol.readString(buffer)
    } yield MetadataUpdate(metadataInfo, stream)
  }
}