package rabbitmq.streaming

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class DeclarePublisherCodecTest extends AnyFlatSpec with Matchers {

  "encode" should "create a buffer with data" in {
    val request = DeclarePublisherRequest(
      publisherId = 1,
      stream = "test-stream",
      publisherReference = None
    )

    val buffer = DeclarePublisherCodec.encode(request, 123)

    buffer.position() should be > 0

    // Optional: verify key bytes
    val bytes = buffer.array()
    bytes(0) shouldBe 0
    bytes(1) shouldBe 1
  }

  "decode" should "parse a valid response buffer" in {
    val buffer = Protocol.allocate(10)
    buffer.putShort(Protocol.Commands.DeclarePublisherResponse)
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(123) // correlationId
    buffer.putShort(Protocol.ResponseCodes.OK)
    buffer.flip()

    val result = DeclarePublisherCodec.decode(buffer)

    result shouldBe Right(DeclarePublisherResponse(123, Protocol.ResponseCodes.OK))
  }

  "decode" should "fail on invalid key" in {
    val buffer = Protocol.allocate(10)
    buffer.putShort(0x9999.toShort) // Invalid key
    buffer.putShort(Protocol.ProtocolVersion)
    buffer.putInt(123)
    buffer.putShort(Protocol.ResponseCodes.OK)
    buffer.flip()

    val result = DeclarePublisherCodec.decode(buffer)

    result shouldBe Left("Invalid key field")
  }
}
