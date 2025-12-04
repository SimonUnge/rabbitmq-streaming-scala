package rabbitmq.streaming
import java.nio.ByteBuffer
import java.net.{Socket, InetSocketAddress}
import java.io.{BufferedInputStream, BufferedOutputStream, DataInputStream, DataOutputStream}
import scala.util.Try

case class ConnectionConfig(
    host: String = "localhost",
    port: Int = 5552,
    username: String = "guest",
    password: String = "guest"
)

class Connection(config: ConnectionConfig) {
  private val socket: Socket = {
    val s = new Socket()
    s.setTcpNoDelay(true)
    s.setKeepAlive(true)
    val addr = new InetSocketAddress(config.host, config.port)
    val connectTimeoutMs = 5000
    s.connect(addr, connectTimeoutMs)
    s
  }

  private val in = new DataInputStream (
    new BufferedInputStream(socket.getInputStream)
  )
  private val out = new DataOutputStream(
    new BufferedOutputStream(socket.getOutputStream)
  )

  def sendFrame(frame: ByteBuffer): Unit = {
    val payloadSize = frame.position()
    out.writeInt(payloadSize)
    out.write(frame.array(), 0, payloadSize)
    out.flush()
  }

  def receiveFrame(): (Short, Short, ByteBuffer) = {
    val payloadSize = in.readInt()
    val buffer = Protocol.allocate(payloadSize)
    in.readFully(buffer.array())
    val key = buffer.getShort()
    val version = buffer.getShort()
    buffer.position(0)
    (key, version, buffer)
  }

  def close(): Unit = {
    Try(in.close())
    Try(out.close())
    Try(socket.close())    
  } 
}
