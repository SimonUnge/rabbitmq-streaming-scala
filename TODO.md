# RabbitMQ Streams Scala Client - TODO

## Completed

**Core Protocol Implementation:**
- All 30 codec files implemented
- Connection management (handshake, heartbeat)
- Stream operations (create, delete)
- Publishing with confirmations
- Subscribing with credit flow control
- Full message routing

**Actor System:**
- ConnectionActor - manages protocol and TCP connection
- PublisherActor - handles publishing with confirm tracking
- SubscriberActor - handles consuming with credit flow
- ConnectionSupervisor - manages connection actors
- PublisherSupervisor - manages publisher actors with per-connection ID allocation
- SubscriberSupervisor - manages subscriber actors with per-connection ID allocation
- RabbitMQSupervisor - top-level supervisor coordinating all three

**Client API:**
- RabbitMQClient - clean Future-based API
- Publisher wrapper - publish() returns Future[Unit]
- Subscriber wrapper - requestCredit() and close()
- createConnection, createPublisher, createSubscriber
- createStream, deleteStream
- Automatic actor spawning and supervision

**Testing:**
- REPL tested end-to-end
- Multiple publishers and subscribers working
- Message delivery confirmed
- Logging with logback

## Next Steps

### 1. Test Full Client API in REPL
- Test createStream/deleteStream
- Test Publisher.publish() with Future handling
- Test multiple publishers/subscribers
- Verify error handling

### 2. Parse Individual Messages from Chunks
- Implement ChunkParser object
- Parse chunk.messages byte array into individual messages
- Each message has length prefix + data
- Update messageHandler signature to deliver parsed messages
- Current: `OsirisChunk => Unit`
- Target: `(Long, Array[Byte]) => Unit` (offset, data)

### 3. Migrate to Pekko Streams TCP
- Replace Connection.scala blocking I/O
- Use Pekko Streams TCP for non-blocking socket operations
- Update ConnectionActor to use Tcp().outgoingConnection
- Remove Future-based frame reading
- Better backpressure and integration with actor system
- Production-ready connection management

### 4. Add Pekko Streams Integration
- Add Source API for consuming
  - `subscriber.source: Source[Message, NotUsed]`
  - Integrates with Pekko Streams pipelines
- Add Sink API for publishing
  - `publisher.sink: Sink[Array[Byte], Future[Done]]`
  - Automatic backpressure via mapAsync
- Keep callback API for simple use cases
- Provide both APIs for flexibility

### 5. Write Documentation
- Update README with:
  - Installation instructions
  - Quick start guide
  - API examples (callback and streams)
  - Architecture overview
  - Configuration options
- Add scaladoc comments to public API
- Document supervision strategies
- Add troubleshooting section

### 6. Add Proper Tests
- Unit tests for codecs
- Integration tests for actors
- Test supervision and restart behavior
- Test error handling
- Test backpressure and credit flow
- Use Pekko TestKit

## Future Enhancements

**Additional Features:**
- Offset tracking and storage (StoreOffset, QueryOffset)
- Super stream support (routing, partitions)
- Stream statistics (StreamStats)
- Publisher sequence recovery (QueryPublisherSequence)
- Single active consumer (ConsumerUpdate)
- Connection pooling in client API
- Automatic reconnection on failure
- Metrics and monitoring hooks

**Code Quality:**
- Add context.watch for automatic deregistration
- Handle max publisher/subscriber ID limits (255)
- Add validation for stream names
- Better error messages
- Performance optimization
- Memory leak prevention

**Developer Experience:**
- Add builder pattern for configuration
- Fluent API for creating publishers/subscribers
- Better type safety (phantom types for connection state)
- Compile-time validation where possible

## Known Issues

- Version handling in codecs needs cleanup (hardcoded checks)
- Frame reader uses blocking I/O (migrate to Pekko Streams TCP)
- No automatic cleanup of dead actors (add context.watch)
- No handling of max ID limits (255 publishers/subscribers per connection)
- Message parsing not implemented (raw chunks only)
- No offset tracking or storage
- No connection pooling
- No automatic reconnection

## Architecture Notes

**Current Design:**
```
RabbitMQClient (user API)
  └─> RabbitMQSupervisor (root)
       ├─> ConnectionSupervisor (manages connections)
       ├─> PublisherSupervisor (manages publishers, tracks IDs per connection)
       └─> SubscriberSupervisor (manages subscribers, tracks IDs per connection)
            ├─> ConnectionActor (protocol + I/O)
            ├─> PublisherActor (publish logic)
            └─> SubscriberActor (consume logic)
```

**Message Flow:**
- Publishing: User → Publisher → PublisherActor → ConnectionActor → RabbitMQ
- Consuming: RabbitMQ → ConnectionActor → SubscriberActor → messageHandler → User
- Routing: ConnectionActor uses correlation IDs, publisher IDs, and subscription IDs

**Supervision Strategy:**
- All actors use restart on failure
- Supervisors track children and manage lifecycle
- Clean separation between transport, protocol, and application layers
