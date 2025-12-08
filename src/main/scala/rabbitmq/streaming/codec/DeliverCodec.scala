package rabbitmq.streaming

import java.nio.ByteBuffer

object DeliverCodec {
  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      version: Short
  ): Either[String, Deliver] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.Deliver,
        (),
        s"Invalid key field"
      )
      subscriptionId = buffer.get()
      committedOffset =
        if (version == 2) {
          Some(buffer.getLong())
        } else {
          None
        }
      osirisChunk <- decodeOsirisChunk(buffer)
    } yield Deliver(subscriptionId, committedOffset, osirisChunk)
  }

  private def decodeOsirisChunk(
      buffer: ByteBuffer
  ): Either[String, OsirisChunk] =
    try {
      val magicVersion = buffer.get()
      val chunkType = decodeChunkType(buffer.get())
      val numEntries = buffer.getShort()
      val numRecords = buffer.getInt()
      val timeStamp = buffer.getLong()
      val epoch = buffer.getLong()
      val chunkFirstOffset = buffer.getLong()
      val chunkCrc = buffer.getInt()
      val dataLength = buffer.getInt()
      val trailerLength = buffer.getInt()
      val bloomSize = buffer.get()
      val reserved = readUint24(buffer) // Helper for 24-bit read
      val messages = new Array[Byte](dataLength)
      buffer.get(messages)

      Right(
        OsirisChunk(
          magicVersion,
          chunkType,
          numEntries,
          numRecords,
          timeStamp,
          epoch,
          chunkFirstOffset,
          chunkCrc,
          dataLength,
          trailerLength,
          bloomSize,
          reserved,
          messages
        )
      )
    } catch {
      case e: Exception =>
        Left(s"Failed to decode OsirisChunk: ${e.getMessage}")
    }

  private def decodeChunkType(value: Byte): ChunkType = value match {
    case 0 => ChunkType.User
    case 1 => ChunkType.TrackingDelta
    case 2 => ChunkType.TrackingSnapshot
    case _ => throw new IllegalArgumentException(s"Unknown chunk type: $value")
  }

  private def readUint24(buffer: ByteBuffer): Int = {
    val byte1 = buffer.get() & 0xff
    val byte2 = buffer.get() & 0xff
    val byte3 = buffer.get() & 0xff
    (byte1 << 16) | (byte2 << 8) | byte3
  }
}
