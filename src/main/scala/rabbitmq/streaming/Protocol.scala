package rabbitmq.streaming

import java.nio.ByteBuffer
import java.nio.ByteOrder
import scala.util.control.Exception.By

object Protocol {

  object Commands {
    val DeclarePublisher: Short = 0x0001
    val DeclarePublisherResponse: Short = 0x8001.toShort
    val Publish: Short = 0x0002
    val DeletePublisher: Short = 0x0003
    val DeletePublisherResponse: Short = 0x8003.toShort
    val QueryPublisherSequence: Short = 0x0004
    val QueryPublisherSequenceResponse: Short = 0x8004.toShort
    val PublishConfirm: Short = 0x0005
    val Credit: Short = 0x0006
    val Subscribe: Short = 0x0007
    val SubscribeResponse: Short = 0x8007.toShort
    val Deliver: Short = 0x0008
    val PublishError: Short = 0x0009
    val Delete: Short = 0x000a.toShort
    val DeleteResponse: Short = 0x800a.toShort
    val Metadata: Short = 0x000b.toShort
    val MetadataResponse: Short = 0x800b.toShort
    val Unsubscribe: Short = 0x000c.toShort
    val UnsubscribeResponse: Short = 0x800c.toShort
    val Create: Short = 0x000d.toShort
    val CreateResponse: Short = 0x800d.toShort
    val Route: Short = 0x000e.toShort
    val RouteResponse: Short = 0x800e.toShort
    val Partitions: Short = 0x000f.toShort
    val PartitionsResponse: Short = 0x800f.toShort
    val QueryOffset: Short = 0x0010
    val QueryOffsetResponse: Short = 0x8010.toShort
    val PeerProperties: Short = 0x0011
    val PeerPropertiesResponse: Short = 0x8011.toShort
    val SaslHandshake: Short = 0x0012
    val SaslHandshakeResponse: Short = 0x8012.toShort
    val SaslAuthenticate: Short = 0x0013
    val SaslAuthenticateResponse: Short = 0x8013.toShort
    val TuneRequest: Short = 0x0014
    val Open: Short = 0x0015
    val OpenResponse: Short = 0x8015.toShort
    val MetadataUpdate: Short = 0x0016
    val Heartbeat: Short = 0x0017
  }

  object Sizes {
    val Key: Int = 2
    val Version: Int = 2
    val CorrelationId: Int = 4
    val StringLength: Int = 2
    val ArrayLength: Int = 4
    val PublisherId: Int = 1
    val ResponseCode: Int = 2
    val SubscriptionId: Int = 1
    val OffsetType: Int = 2
    val Offset: Int = 8
    val Timestamp: Int = 8
    val Credit: Int = 2
  }

  object ResponseCodes {
    val OK: Short = 0x01
    val StreamDoesNotExist: Short = 0x02
    val StreamAlreadyExists: Short = 0x05
    val AccessRefused: Short = 0x10
  }

  val ProtocolVersion: Short = 1

  def allocate(size: Int): ByteBuffer = {
    ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
  }

  def writeString(buffer: ByteBuffer, str: String): Unit = {
    val bytes = str.getBytes("UTF-8")
    buffer.putShort(bytes.length.toShort)
    buffer.put(bytes)
  }

  def writeOptionalString(buffer: ByteBuffer, strOpt: Option[String]): Unit = {
    strOpt match {
      case Some(str) =>
        writeString(buffer, str)
      case None =>
        buffer.putShort(0.toShort)
    }
  }

  def readString(buffer: ByteBuffer): String = {
    val length = buffer.getShort().toInt
    val bytes = new Array[Byte](length)
    buffer.get(bytes)
    new String(bytes, "UTF-8")
  }

  def writeBytes(buffer: ByteBuffer, bytes: Array[Byte]): Unit = {
    buffer.putInt(bytes.length)
    buffer.put(bytes)
  }

  def writeOptionalBytes(
      buffer: ByteBuffer,
      bytesOpt: Option[Array[Byte]]
  ): Unit = {
    bytesOpt match {
      case Some(bytes) =>
        writeBytes(buffer, bytes)
      case None =>
        buffer.putInt(-1)
    }
  }

  def readBytes(buffer: ByteBuffer): Array[Byte] = {
    val length = buffer.getInt()
    val bytes = new Array[Byte](length)
    buffer.get(bytes)
    bytes
  }

  def readOptionalBytes(buffer: ByteBuffer): Option[Array[Byte]] = {
    if (!buffer.hasRemaining) {
      None
    } else {
      val length = buffer.getInt()
      if (length == -1) {
        None
      } else {
        val bytes = new Array[Byte](length)
        buffer.get(bytes)
        Some(bytes)
      }
    }
  }
}
