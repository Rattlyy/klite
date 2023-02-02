package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toBeAnInstanceOf
import ch.tutteli.atrium.api.fluent.en_GB.toBeTheInstance
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.math.BigDecimal.ONE
import java.math.BigDecimal.TEN
import java.sql.Connection
import java.sql.Date
import java.sql.Timestamp
import java.time.*
import java.time.ZoneOffset.UTC
import java.util.*
import java.util.UUID.randomUUID

class JdbcConverterTest {
  @Test fun `null`() {
    expect(JdbcConverter.to(null)).toEqual(null)
  }

  @Test fun `primitive types are supported by jdbc`() {
    expect(JdbcConverter.to(123)).toEqual(123)
    expect(JdbcConverter.to(123L)).toEqual(123L)
    expect(JdbcConverter.to(123.0)).toEqual(123.0)
  }

  @Test fun `BigInteger and BigDecimal`() {
    expect(JdbcConverter.to(123.toBigDecimal())).toEqual(123.toBigDecimal())
    expect(JdbcConverter.to(123.toBigInteger())).toEqual(123.toBigInteger())
  }

  @Test fun `to array of varchar`() {
    val conn = mockk<Connection>(relaxed = true)
    expect(JdbcConverter.to(listOf("hello"), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("varchar", arrayOf("hello")) }

    expect(JdbcConverter.to(setOf("set"), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("varchar", arrayOf("set")) }

    expect(JdbcConverter.to(arrayOf("a1", "a2"), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("varchar", arrayOf("a1", "a2")) }
  }

  @Test fun `to array of uuid`() {
    val conn = mockk<Connection>(relaxed = true)
    val uuid = randomUUID()
    expect(JdbcConverter.to(listOf(uuid, uuid), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("uuid", arrayOf(uuid, uuid)) }
  }

  @Test fun `to array of BigDecimal`() {
    val conn = mockk<Connection>(relaxed = true)
    expect(JdbcConverter.to(listOf(ONE, TEN), conn)).toBeAnInstanceOf<java.sql.Array>()
    verify { conn.createArrayOf("numeric", arrayOf(ONE, TEN)) }
  }

  @Test fun `to local date and time`() {
    expect(JdbcConverter.to(LocalDate.of(2021, 10, 21))).toEqual(LocalDate.of(2021, 10, 21))
    expect(JdbcConverter.to(LocalDateTime.MIN)).toBeTheInstance(LocalDateTime.MIN)
    expect(JdbcConverter.to(LocalTime.MIN)).toBeTheInstance(LocalTime.MIN)
    expect(JdbcConverter.to(OffsetDateTime.MIN)).toBeTheInstance(OffsetDateTime.MIN)
  }

  @Test fun `from local date and time`() {
    expect(JdbcConverter.from(Date(123), LocalDate::class)).toEqual(LocalDate.of(1970, 1, 1))
    expect(JdbcConverter.from(Timestamp(123), Instant::class)).toEqual(Instant.ofEpochMilli(123))
    expect(JdbcConverter.from(Timestamp(123), LocalDateTime::class)).toEqual(Timestamp(123).toLocalDateTime())
    expect(JdbcConverter.from(null, LocalDateTime::class)).toEqual(null)
    expect(JdbcConverter.from(null, Instant::class)).toEqual(null)
  }

  @Test fun `Instant should be converted to offset`() {
    expect(JdbcConverter.to(Instant.EPOCH)).toEqual(Instant.EPOCH.atOffset(UTC))
  }

  @Test fun `toString types`() {
    expect(JdbcConverter.to(Currency.getInstance("EUR"))).toEqual("EUR")
    expect(JdbcConverter.to(Locale("et"))).toEqual("et")
    expect(JdbcConverter.to(Period.ofDays(3))).toEqual("P3D")
  }

  @Test fun `from toString types`() {
    expect(JdbcConverter.from("EUR", Currency::class)).toEqual(Currency.getInstance("EUR"))
    expect(JdbcConverter.from("et", Locale::class)).toEqual(Locale("et"))
    expect(JdbcConverter.from("P3D", Period::class)).toEqual(Period.ofDays(3))
  }
}
