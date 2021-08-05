import java.time.Instant

package object utils {
  def getCurrentTimeSeconds(): Long = {
    Instant.now().getEpochSecond()
  }
}
