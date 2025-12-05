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
