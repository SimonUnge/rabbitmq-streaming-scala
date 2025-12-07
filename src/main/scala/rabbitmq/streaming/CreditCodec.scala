package rabbitmq.streaming

import java.nio.ByteBuffer

object CreditCodec {
  def encode(request: CreditRequest): ByteBuffer = {
    val totalSize = Protocol.Sizes.Key +
      Protocol.Sizes.Version +
      Protocol.Sizes.SubscriptionId +
      Protocol.Sizes.Credit

    val buffer = Protocol.allocate(totalSize)

    buffer.putShort(Protocol.Commands.Credit)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.put(request.subscriptionId)
    buffer.putShort(request.credit)

    buffer
  }
}