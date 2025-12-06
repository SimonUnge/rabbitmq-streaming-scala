package rabbitmq.streaming

// Offset specification for where to start consuming
sealed trait OffsetSpecification
object OffsetSpecification {
  case object First extends OffsetSpecification
  case object Last extends OffsetSpecification  
  case object Next extends OffsetSpecification
  case class Offset(value: Long) extends OffsetSpecification
  case class Timestamp(value: Long) extends OffsetSpecification
}

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