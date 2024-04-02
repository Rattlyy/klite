package klite

import ch.tutteli.atrium.api.fluent.en_GB.*
import ch.tutteli.atrium.api.verbs.expect
import org.junit.jupiter.api.Test

class DependencyInjectingRegistryTest {
  val registry = DependencyInjectingRegistry()

  @Test fun require() {
    expect(registry.require<TextBodyParser>()).toBeAnInstanceOf<TextBodyParser>()
  }

  @Test fun optional() {
    expect(registry.optional<BodyParser>()).toEqual(null)

    registry.register<BodyParser>(TextBodyParser())
    expect(registry.optional<BodyParser>()).toBeAnInstanceOf<TextBodyParser>()
  }

  @Test fun `use registered instance even for arguments having default values`() {
    class Subject(val nonDefault: BodyParser = TextBodyParser(), val default: FormUrlEncodedParser = FormUrlEncodedParser())

    val registeredParser = TextBodyParser()
    registry.register<BodyParser>(registeredParser)
    expect(registry.require<Subject>()) {
      its { nonDefault }.toBeTheInstance(registeredParser)
      its { default }.notToBeTheInstance(registry.require())
    }
  }

  @Test fun requireAll() {
    expect(registry.requireAll<TextBodyParser>()).toBeEmpty()

    expect(registry.require<TextBodyParser>()).toBeAnInstanceOf<TextBodyParser>()
    registry.register<FormUrlEncodedParser>()

    expect(registry.requireAll<BodyParser>()).toHaveSize(2)
  }
}
