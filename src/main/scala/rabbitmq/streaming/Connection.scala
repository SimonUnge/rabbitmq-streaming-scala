package rabbitmq.streaming
import java.net.{Socket, InetSocketAddress}
import java.io.{BufferedInputStream, BufferedOutputStream}

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

  private val in: BufferedInputStream = new BufferedInputStream(
    socket.getInputStream
  )
  private val out: BufferedOutputStream = new BufferedOutputStream(
    socket.getOutputStream
  )
  // Method to send a frame
  // Method to receive a frame
  // Method to close connection
}
