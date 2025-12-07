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

### Credit Field
**Subscribe Command**:
- Model defines `credit: Int` (for API convenience)
- Wire protocol expects `Short` (2 bytes)
- Codec converts: `buffer.putShort(request.credit.toShort)`

### Publisher Reference
**DeclarePublisher Command**:
- Optional field encoded as `0` for None (not `-1`)
- Uses `Protocol.writeOptionalString()` which writes empty string for None

## Version Handling

### Command vs Protocol Versions
- **Protocol Version**: Global version (currently 1)
- **Command Versions**: Per-command versions (independent evolution)
- Most codecs ignore received version parameter
- **Deliver Command**: Uses version for format decisions (V1 vs V2 with committedOffset)

### Version Validation Removed
Originally implemented strict version checking:
```scala
if (version != Protocol.ProtocolVersion) return Left("Unsupported version")
```
Removed because command versions are independent of protocol version.

## Server Error Patterns

### Function Clause Errors
**Symptom**: `error:function_clause` in `rabbit_stream_core:parse_map`
**Cause**: Incorrect map encoding (wrong size calculation or missing conditional logic)
**Solution**: Match exact server expectations for each command

### EOF Exceptions
**Symptom**: `java.io.EOFException` on client side
**Cause**: Server closes connection due to malformed request
**Debug**: Check server logs for `function_clause` or parsing errors

## Debugging Techniques

### Binary Protocol Debugging
```scala
println(s"Buffer bytes=${buffer.array().take(buffer.position()).map(b => f"$b%02x").mkString(",")}")
```

### Server Log Analysis
- Look for `function_clause` errors in RabbitMQ logs
- Hex dumps show exact bytes received by server
- Compare working vs failing requests

## Protocol Design Patterns

### Consistent Patterns
- All commands: Key (2) + Version (2) + CorrelationId (4)
- String encoding: Length (2) + UTF-8 bytes
- Response format: CorrelationId (4) + ResponseCode (2)

### Inconsistent Patterns
- Map encoding varies by command (always vs conditional)
- Field types vary (Int vs Short for similar concepts)
- Optional field encoding differs across commands

## Lessons Learned

1. **Protocol specs can be incomplete** - Real behavior differs from documentation
2. **Server errors are cryptic** - Binary protocol debugging requires patience
3. **Each command is unique** - Don't assume consistency across commands
4. **Trial and error works** - Sometimes empirical testing beats documentation
5. **Debug prints are essential** - Binary protocol needs visibility into encoding

## Recommendations

1. **Test each codec individually** against real server
2. **Add comprehensive logging** for binary encoding/decoding
3. **Document discovered quirks** as you find them
4. **Use server logs** for debugging malformed requests
5. **Don't assume consistency** between similar commands