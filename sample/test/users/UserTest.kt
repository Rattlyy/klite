package users

import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import klite.json.JsonMapper
import klite.json.parse
import org.junit.jupiter.api.Test
import users.TestData.address
import users.User.Address

class UserTest {
  val json = JsonMapper()

  @Test fun json() {
    val addressJson = json.render(address)
    expect(addressJson).toEqual("""{"city":"Tallinn","countryCode":"EE","id":"a7f033bc-04f3-4cc0-94f1-564783cab08f","userId":"d7242ad0-11ea-4991-93af-7f9e08784bce"}""")
    expect(json.parse<Address>(addressJson)).toEqual(address)
  }
}

