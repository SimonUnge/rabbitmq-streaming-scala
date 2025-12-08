# RabbitMQ Streams Protocol Quirks & Inconsistencies

This document captures the protocol inconsistencies and quirks discovered during implementation that deviate from expected patterns or documentation.

## Map/Properties Encoding Inconsistencies

### Commands with Always-Present Maps
These commands always encode the map size, even when empty:

**Create Command**:
- Always encodes `arguments.size` (4 bytes) + entries
- Empty map: encodes `size=0`, no entries
- Works correctly with server

**PeerProperties Command**:
- Always encodes `properties.size` (4 bytes) + entries  
- Empty map: encodes `size=0`, no entries
- Works correctly with server

### Commands with Conditional Maps
**Subscribe Command**:
- Only encodes properties when `nonEmpty`
- Empty map: omits entirely (no size field, no entries)
- Encoding empty map as `size=0` causes server `function_clause` error
- **Server expectation**: Complete omission when empty

## Offset Specification Encoding

**Subscribe Command Offset Types**:
- Types 1-3 (First/Last/Next): Only encode type (2 bytes), no value
- Types 4-5 (Offset/Timestamp): Encode type (2 bytes) + value (8 bytes)
- **Variable length encoding** based on offset type

## Field Type Inconsistencies

### Publisher Reference
**DeclarePublisher Command**:
- Optional field encoded as `0` for None (not `-1`)

## Protocol Design Patterns

### Consistent Patterns
- All commands: Key (2) + Version (2) + CorrelationId (4)
- String encoding: Length (2) + UTF-8 bytes
- Response format: CorrelationId (4) + ResponseCode (2)

### Inconsistent Patterns
- Map encoding varies by command (always vs conditional)
- Field types vary (Int vs Short for similar concepts)
- Optional field encoding differs across commands
