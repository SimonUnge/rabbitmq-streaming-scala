package rabbitmq.streaming

import java.nio.ByteBuffer
import java.nio.ByteOrder

object Protocol {
    
    object Commands {
        val DeclarePublisher: Short = 0x0001
        val DeclarePublisherResponse: Short = 0x8001.toShort
        val PeerProperties: Short = 0x0011
        val PeerPropertiesResponse: Short = 0x8011.toShort
    }

    object ResponseCodes {
        val OK: Short = 0x01
        val StreamDoesNotExist: Short = 0x02
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
                buffer.putShort(-1.toShort)
        }
    }

    def readString(buffer: ByteBuffer): String = {
        val length = buffer.getShort().toInt
        val bytes = new Array[Byte](length)
        buffer.get(bytes)
        new String(bytes, "UTF-8")
    }
}