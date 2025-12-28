package rabbitmq.streaming

import org.apache.pekko.actor.typed.ActorRef

class Subscriber(actor: ActorRef[SubscriberActor.Command]) {
  def requestCredit(credit: Short): Unit = {
    actor ! SubscriberActor.RequestCredit(credit)
  }
  def close(): Unit = {
    actor ! SubscriberActor.Close
  }
}
