# RabbitMQ Streaming Scala Client

A native Scala implementation of the RabbitMQ Streams protocol using Pekko actors.

Built from scratch as a learning project to understand both Scala and the RabbitMQ Streams protocol.

## Features

- Full RabbitMQ Streams protocol implementation (30 commands)
- Actor-based architecture with supervision
- Clean Future-based client API
- Publish with confirmations
- Subscribe with credit flow control
- Batch publishing support
- Individual message parsing from chunks
- Automatic backpressure via credit flow

## Quick Start

```scala
import rabbitmq.streaming._
import scala.concurrent.Await
import scala.concurrent.duration._

// Create client
val client = RabbitMQClient()

// Create connection
val conn = Await.result(client.createConnection(ConnectionConfig(
  host = "localhost",
  port = 5552,
  username = "guest",
  password = "guest"
)), 10.seconds)

// Create stream
Await.result(client.createStream(conn, "mystream"), 5.seconds)

// Create subscriber
val subscriber = Await.result(client.createSubscriber(
  conn,
  "mystream",
  OffsetSpecification.First,
  initialCredit = 10,
  messageHandler = (offset, data) => {
    println(s"[Offset $offset] ${new String(data)}")
  }
), 5.seconds)

// Create publisher
val publisher = Await.result(client.createPublisher(conn, "mystream"), 5.seconds)

// Publish single message
Await.result(publisher.publish("Hello!".getBytes), 5.seconds)

// Publish batch
Await.result(publisher.publishBatch(List(
  "Message 1".getBytes,
  "Message 2".getBytes,
  "Message 3".getBytes
)), 5.seconds)

// Cleanup
subscriber.close()
publisher.close()
client.shutdown()
```

## Architecture

```
RabbitMQClient (user API)
  └─> RabbitMQSupervisor (root)
       ├─> ConnectionSupervisor (manages connections)
       ├─> PublisherSupervisor (manages publishers)
       └─> SubscriberSupervisor (manages subscribers)
            ├─> ConnectionActor (protocol + I/O)
            ├─> PublisherActor (publish logic)
            └─> SubscriberActor (consume logic)
```

## Building

```bash
sbt compile
sbt test
```

## Testing in REPL

```bash
sbt console
```

Then follow the Quick Start example above.

## Configuration

Connection configuration via `ConnectionConfig`:

```scala
ConnectionConfig(
  host = "localhost",      // RabbitMQ host
  port = 5552,            // Streams port (not 5672!)
  username = "guest",     // Username
  password = "guest"      // Password
)
```

Or use environment variables:

```bash
export RABBITMQ_HOST=localhost
export RABBITMQ_PORT=5552
export RABBITMQ_USERNAME=guest
export RABBITMQ_PASSWORD=guest
```

## Status

Working implementation with:
- Full protocol support
- Actor-based architecture
- Supervision and fault tolerance
- Clean client API
- Message parsing
- Batch publishing

See TODO.org for planned enhancements.

## Learning Resources

This project was built to learn:
- Scala programming
- Pekko actors (Erlang/OTP concepts in Scala)
- Binary protocol implementation
- Actor supervision patterns
- Future-based async programming

## License

TBD
