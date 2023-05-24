package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toBeTheInstance
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class TransactionTest {
  val db = mockk<ConfigDataSource>(relaxed = true)

  @Test fun `transaction does not open connection on creation`() {
    val tx = Transaction(db)
    verify(exactly = 0) { db.connection }
    tx.close(true)
    verify(exactly = 0) { db.connection }
  }

  @Test fun `transaction creates and reuses connection on demand`() {
    val tx = Transaction(db).attachToThread()
    val conn = db.withConnection { this }
    expect(db.withConnection { this }).toBeTheInstance(conn)
    verify { conn.autoCommit = false }
    tx.close(true)
    verify(exactly = 1) {
      db.connection.apply {
        commit()
        autoCommit = true
        close()
      }
    }
  }

  @Test fun `transaction with rollbackOnly rolls back`() {
    val tx = Transaction(db).attachToThread()
    val conn = db.withConnection { this }
    verify { conn.autoCommit = false }
    tx.close(false)
    verify(exactly = 1) {
      db.connection.apply {
        rollback()
        autoCommit = true
        close()
      }
    }
  }

  @Test fun `connection without transaction is closed`() {
    val conn = db.withConnection { this }
    verify { conn.close() }
    verify(exactly = 0) { conn.autoCommit = any() }
  }
}
