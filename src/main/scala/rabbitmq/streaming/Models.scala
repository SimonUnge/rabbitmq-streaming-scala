package rabbitmq.streaming

case class DeclarePublisherRequest(
    publisherId: Byte,
    stream: String,
    publisherReference: Option[String] = None
)

case class DeclarePublisherResponse(
    correlationId: Int,
    responseCode: Short
)

sealed trait DeclarePublisherResult
object DeclarePublisherResult {
  case object Success extends DeclarePublisherResult
  case object StreamDoesNotExist extends DeclarePublisherResult
  case object AccessRefused extends DeclarePublisherResult
  case class UnknownError(code: Short)
      extends DeclarePublisherResult // catch-all
}

case class PeerPropertiesRequest(
    properties: Map[String, String]
)

case class PeerPropertiesResponse(
    correlationId: Int,
    responseCode: Short,
    properties: Map[String, String]
)

sealed trait PeerPropertiesResult
object PeerPropertiesResult {
  case class Success(serverProperties: Map[String, String])
      extends PeerPropertiesResult
  case class UnknownError(code: Short) extends PeerPropertiesResult // catch-all
}

case object SaslHandshakeRequest
case class SaslHandshakeResponse(
    correlationId: Int,
    responseCode: Short,
    mechanisms: List[String]
)

sealed trait SaslHandshakeResult
object SaslHandshakeResult {
  case class Success(mechanisms: List[String]) extends SaslHandshakeResult
  case class UnknownError(code: Short) extends SaslHandshakeResult
}

case class SaslAuthenticateRequest(
    mechanism: String,
    saslOpaqueData: Array[Byte]
)

case class SaslAuthenticateResponse(
    correlationId: Int,
    responseCode: Short,
    saslOpaqueData: Option[Array[Byte]]
)

sealed trait SaslAuthenticateResult
object SaslAuthenticateResult {
  case class Success(saslOpaqueData: Option[Array[Byte]])
      extends SaslAuthenticateResult
  case class UnknownError(code: Short) extends SaslAuthenticateResult
}

case class TuneRequest(
    frameMax: Int,
    heartbeat: Int
)

case class OpenRequest(
    virtualHost: String
)

case class OpenResponse(
    correlationId: Int,
    responseCode: Short,
    connectionProperties: Map[String, String]
)

sealed trait OpenResult
object OpenResult {
  case class Success(connectionProperties: Map[String, String])
      extends OpenResult
  case class UnknownError(code: Short) extends OpenResult
}

case class CreateRequest(
    stream: String,
    arguments: Map[String, String] = Map.empty
)

case class CreateResponse(
    correlationId: Int,
    responseCode: Short
)

case class PublishRequest(
    publisherId: Byte,
    publishedMessages: List[PublishedMessage]
)

case class PublishedMessage(
    publishingId: Long,
    message: Array[
      Byte
    ], // Note: Arrays use reference equality, not content equality
    filterValue: Option[String] = None
)

case class SubscribeRequest(
    subscriptionId: Byte,
    stream: String,
    offsetSpecification: OffsetSpecification,
    credit: Short,
    properties: Map[String, String] = Map.empty
)

case class SubscribeResponse(
    correlationId: Int,
    responseCode: Short
)
sealed trait OffsetSpecification
object OffsetSpecification {
  case object First extends OffsetSpecification // Type = 1, no value needed
  case object Last extends OffsetSpecification // Type = 2, no value needed
  case object Next extends OffsetSpecification // Type = 3, no value needed
  case class Offset(value: Long)
      extends OffsetSpecification // Type = 4, value = the Long
  case class Timestamp(value: Long)
      extends OffsetSpecification // Type = 5, value = the Long
}

case class Deliver(
    subscriptionId: Byte,
    committedOffset: Option[Long],
    osirisChunk: OsirisChunk
)

case class OsirisChunk(
    magicVersion: Byte,
    chunkType: ChunkType,
    numEntries: Short,
    numRecords: Int,
    timeStamp: Long,
    epoch: Long,
    chunkFirstOffset: Long,
    chunkCrc: Int,
    dataLength: Int,
    trailerLength: Int,
    bloomSize: Byte,
    reserved: Int,
    messages: Array[Byte]
)

sealed trait ChunkType
object ChunkType {
  case object User extends ChunkType
  case object TrackingDelta extends ChunkType
  case object TrackingSnapshot extends ChunkType
}

// Credit Command
case class CreditRequest(
    subscriptionId: Byte,
    credit: Short
)

// Delete Command
case class DeleteRequest(
    stream: String
)

case class DeleteResponse(
    correlationId: Int,
    responseCode: Short
)

// Delete Publisher Command
case class DeletePublisherRequest(
    publisherId: Byte
)

case class DeletePublisherResponse(
    correlationId: Int,
    responseCode: Short
)

// Query Publisher Sequence Command
case class QueryPublisherSequenceRequest(
    publisherReference: String,
    stream: String
)

case class QueryPublisherSequenceResponse(
    correlationId: Int,
    responseCode: Short,
    sequence: Long
)

// Publish Confirm Command (server-initiated)
case class PublishConfirm(
    publisherId: Byte,
    publishingIds: List[Long]
)

// Publish Error Command (server-initiated)
case class PublishError(
    publisherId: Byte,
    publishingErrors: List[PublishingError]
)

case class PublishingError(
    publishingId: Long,
    errorCode: Short
)

// Unsubscribe Command
case class UnsubscribeRequest(
    subscriptionId: Byte
)

case class UnsubscribeResponse(
    correlationId: Int,
    responseCode: Short
)

// Query Offset Command
case class QueryOffsetRequest(
    reference: String,
    stream: String
)

case class QueryOffsetResponse(
    correlationId: Int,
    responseCode: Short,
    offset: Long
)

// Store Offset Command
case class StoreOffsetRequest(
    reference: String,
    stream: String,
    offset: Long
)

case class StoreOffsetResponse(
    correlationId: Int,
    responseCode: Short
)

// Metadata Command
case class MetadataRequest(
    streams: List[String]
)

case class MetadataResponse(
    correlationId: Int,
    responseCode: Short,
    brokers: List[Broker],
    streamMetadata: List[StreamMetadata]
)

case class Broker(
    reference: Short,
    host: String,
    port: Int
)

case class StreamMetadata(
    streamName: String,
    responseCode: Short,
    leaderReference: Short,
    replicaReferences: List[Short]
)

// Route Command (Super Streams)
case class RouteRequest(
    routingKey: String,
    superStream: String
)

case class RouteResponse(
    correlationId: Int,
    responseCode: Short,
    streams: List[String]
)

// Partitions Command (Super Streams)
case class PartitionsRequest(
    superStream: String
)

case class PartitionsResponse(
    correlationId: Int,
    responseCode: Short,
    streams: List[String]
)

// Metadata Update (server-initiated)
case class MetadataUpdate(
    metadataInfo: Short,
    stream: String
)

// Heartbeat Command
case object HeartbeatRequest

// Close Command
case class CloseRequest(
    reason: String
)

case class CloseResponse(
    correlationId: Int,
    responseCode: Short
)

// Consumer Update (server-initiated)
case class ConsumerUpdate(
    subscriptionId: Byte,
    active: Boolean
)

// Exchange Command
case class ExchangeRequest(
    exchange: String,
    routingKey: String,
    message: Array[Byte]
)

case class ExchangeResponse(
    correlationId: Int,
    responseCode: Short
)

// Stream Stats Command
case class StreamStatsRequest(
    stream: String
)

case class StreamStatsResponse(
    correlationId: Int,
    responseCode: Short,
    stats: Map[String, Long]
)

// Create Super Stream Command
case class CreateSuperStreamRequest(
    name: String,
    partitions: List[String],
    bindingKeys: List[String],
    arguments: Map[String, String] = Map.empty
)

case class CreateSuperStreamResponse(
    correlationId: Int,
    responseCode: Short
)

// Delete Super Stream Command
case class DeleteSuperStreamRequest(
    name: String
)

case class DeleteSuperStreamResponse(
    correlationId: Int,
    responseCode: Short
)
