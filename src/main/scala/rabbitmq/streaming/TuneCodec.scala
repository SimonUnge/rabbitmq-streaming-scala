package rabbitmq.streaming
import java.nio.ByteBuffer

object TuneCodec {
  // Client receives from server
  def decode(
      buffer: ByteBuffer,
      expectedKey: Short,
      expectedVersion: Short
  ): Either[String, TuneRequest] = {
    for {
      key <- Either.cond(
        expectedKey == Protocol.Commands.TuneRequest,
        (),
        s"Invalid key field"
      )
      version <- Either.cond(
        expectedVersion == Protocol.ProtocolVersion,
        (),
        s"Incompatible protocol version"
      )
      frameMax = buffer.getInt()
      heartbeat = buffer.getInt()
    } yield TuneRequest(frameMax, heartbeat)
  }

  // Client sends back to server
  def encode(tune: TuneRequest): ByteBuffer = {
    val totalSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.CorrelationId +
      Protocol.Sizes.CorrelationId // Heartbeat (same size as CorrelationId)
    val buffer = Protocol.allocate(totalSize)
    buffer.putShort(Protocol.Commands.TuneRequest)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(tune.frameMax)
    buffer.putInt(tune.heartbeat)
    buffer
  }
}
