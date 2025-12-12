# RabbitMQ Streams Scala Client - TODO

## Commands to Implement Yourself (Educational Value)

### 1. Publish Command - COMPLETED
- **File**: `PublishCodec.scala`
- **Models**: `PublishRequest`, `PublishedMessage` (DONE)
- **Learning**: Fire-and-forget commands, message arrays, publishing IDs
- **Spec**: Version 1 (no filter) and Version 2 (with filter)
- **Note**: No response expected - just encode, no decode needed
- **Status**: Working in SimpleClient

### 2. Subscribe Command - COMPLETED
- **File**: `SubscribeCodec.scala`
- **Models**: `SubscribeRequest`, `SubscribeResponse`, `OffsetSpecification` (DONE)
- **Learning**: Offset types (first, last, timestamp, offset), consumer properties, flow control
- **Spec**: Complex request with offset specifications and properties
- **Status**: Working encode and decode methods

### 3. Deliver Command - COMPLETED
- **File**: `DeliverCodec.scala` 
- **Models**: `Deliver`, `OsirisChunk`, `ChunkType` (DONE)
- **Learning**: Server-initiated messages, complex binary format, streaming data
- **Spec**: Version 1 and Version 2 (with committed offset)
- **Status**: Working decode with full OsirisChunk parsing

## Commands to Auto-Generate (Repetitive Patterns)

### Standard Request/Response Pattern
- **PublishConfirm** - Array of confirmed publishing IDs
- **PublishError** - Array of failed publishing IDs with error codes  
- **Credit** - Flow control for consumers
- **Unsubscribe** - Stop consuming from subscription
- **Delete** - Delete a stream
- **QueryOffset** - Get stored offset for consumer
- **QueryPublisherSequence** - Get last sequence for publisher recovery

### Complex but Repetitive
- **Metadata** - Query stream/broker metadata (arrays of broker/stream info)
- **MetadataUpdate** - Server-sent metadata changes
- **Route** - Super stream routing queries
- **Partitions** - Super stream partition queries
- **ConsumerUpdate** - Single active consumer updates
- **StreamStats** - Stream statistics queries
- **CreateSuperStream/DeleteSuperStream** - Super stream management

## Implementation Order Recommendation

1. **Publish** - Basic message sending complete
   - Models complete
   - `PublishCodec.encode()` implemented
   - Working in SimpleClient
2. **Subscribe** - Basic message receiving complete
   - Models complete
   - `SubscribeCodec.encode()` and `decode()` implemented
3. **Deliver** - Handle incoming messages complete
   - Models complete
   - `DeliverCodec.decode()` with full OsirisChunk parsing
   - Version-aware (V1/V2) with committedOffset support
4. **Auto-generate the rest** - Complete the protocol

## Current Status

**COMPLETED:**
- All connection/authentication codecs
- Stream creation
- Publisher declaration
- Publish command (models + codec)
- Subscribe command (models + codec)
- Deliver command (models + codec)
- Working message publishing in SimpleClient
- Complete producer-consumer protocol implementation

**CORE PROTOCOL COMPLETE:**
- All 3 educational commands implemented
- Ready for end-to-end streaming

**TODO:**
- **Fix version handling in all codecs** - Remove hardcoded `Protocol.ProtocolVersion` checks in decode methods (DeclarePublisherCodec, SubscribeCodec, etc.) and handle command-specific versions properly
- Integration testing with full publish/subscribe flow
- Additional protocol commands (optional extensions)

**FOR 1.0 RELEASE:**
- **Migrate to Pekko Streams TCP** - Replace `Connection.scala` blocking I/O with Pekko Streams TCP for:
  - Non-blocking socket operations
  - Proper backpressure handling
  - Better integration with actor system
  - Production-ready connection management
- Current `Connection.scala` + `Future` approach is fine for learning/prototyping
- **Add Connection.apply() factory** - Add companion object with `apply` method to avoid `new Connection(config)` (more idiomatic Scala)

## Notes

- All auto-generated commands follow patterns you've already learned
- Focus on the 3 educational commands first
- The rest can be batch-generated once you understand the core concepts
