package rabbitmq.streaming

import java.nio.ByteBuffer

object HeartbeatCodec {
  def encode(): ByteBuffer = {
    val totalSize = Protocol.Sizes.Key + Protocol.Sizes.Version

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Heartbeat)
    buffer.putShort(Protocol.ProtocolVersion)

    buffer
  }
}