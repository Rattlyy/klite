package klite.jdbc

import ch.tutteli.atrium.api.fluent.en_GB.toBeEmpty
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.sql.ResultSet

class ValuesTest {
  @Test fun toValues() {
    val data = SomeData("Hello", 123)
    expect(data.toValues()).toEqual(mapOf("hello" to "Hello", "world" to 123, "nullable" to null))
    expect(data.toValues(SomeData::world to 124)).toEqual(mapOf("hello" to "Hello", "world" to 124, "nullable" to null))
  }

  @Test fun toValuesSkipping() {
    val data = SomeData("Hello", 123)
    expect(data.toValuesSkipping(SomeData::hello, SomeData::nullable)).toEqual(mapOf("world" to 123))
    expect(data.toValuesSkipping(SomeData::hello, SomeData::world, SomeData::nullable)).toBeEmpty()
  }

  @Test fun fromValues() {
    val rs = mockk<ResultSet> {
      every { getObject("hello") } returns "Hello"
      every { getObject("world") } returns 42
      every { getObject("nullable") } returns null
    }
    expect(rs.fromValues<SomeData>()).toEqual(SomeData("Hello", 42))
  }

  @Test fun `fromValues with some values provided`() {
    val rs = mockk<ResultSet>()
    expect(rs.fromValues(SomeData::hello to "Hello", SomeData::world to 42, SomeData::nullable to null)).toEqual(SomeData("Hello", 42))
  }

  data class SomeData(val hello: String, val world: Int, val nullable: String? = null)
}
