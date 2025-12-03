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
  case class UnknownError(code: Short) extends DeclarePublisherResult  // catch-all
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
    case class Success(serverProperties: Map[String, String]) extends PeerPropertiesResult
    case class UnknownError(code: Short) extends PeerPropertiesResult  // catch-all
}