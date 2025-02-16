package klite

import ch.tutteli.atrium.api.fluent.en_GB.toBeGreaterThan
import ch.tutteli.atrium.api.fluent.en_GB.toBeLessThanOrEqualTo
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test
import java.lang.System.currentTimeMillis
import java.util.concurrent.atomic.AtomicLong

typealias Id = TSID<Any>

class TSIDTest {
  val maxValue = Id(Long.MAX_VALUE)

  @Test fun tsid() {
    expect(Id(1234L).toString()).toEqual("ya")
    expect(maxValue.toString()).toEqual("1y2p0ij32e8e7")
    expect(Id("1y2p0ij32e8e7")).toEqual(maxValue)
  }

  @Test fun converter() {
    expect(Converter.from<Id>(maxValue.toString())).toEqual(maxValue)
  }

  @Test fun createdAt() {
    expect(Id().createdAt.toEpochMilli()).toBeLessThanOrEqualTo(currentTimeMillis())
  }

  @Test fun `no collisions`() {
    val ids = mutableSetOf<Id>()
    for (i in 1..1000000) ids.add(Id())
    expect(ids.size).toEqual(1000000)
  }

  @Test
  fun deterministic() {
    TSID.deterministic = AtomicLong(123123123)
    expect(TSID<Any>().toString()).toEqual("21ayes")
    expect(TSID<Any>().toString()).toEqual("21ayet")
    expect(TSID<Any>().toString()).toEqual("21ayeu")
    TSID.deterministic = null
    expect(TSID<Any>().toString().length).toBeGreaterThan(8)
  }
}
