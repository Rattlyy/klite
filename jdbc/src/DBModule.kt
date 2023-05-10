package klite.jdbc

import klite.Config
import klite.Extension
import klite.Server
import klite.StatusCode.Companion.Conflict
import klite.register
import javax.sql.DataSource

val Config.dbPoolMaxSize get() = optional("DB_POOL_SIZE")?.toInt() ?: ((optional("NUM_WORKERS")?.toInt() ?: 5) + (optional("JOB_WORKERS")?.toInt() ?: 5))

/** See [HikariModule] if you want to use Hikari */
open class DBModule(val dataSource: DataSource = PooledDataSource(ConfigDataSource())): Extension {
  override fun install(server: Server) = server.run {
    registry.register<DataSource>(dataSource)
    errors.on(AlreadyExistsException::class, Conflict)
    onStop { (dataSource as? AutoCloseable)?.close() }
  }
}

/** Use before DBModule to add support for Heroku-like DATABASE_URL env variable (non-jdbc) */
fun initHerokuDB(suffix: String = "?sslmode=require") {
  val url = Config.optional("DATABASE_URL") ?: return
  val m = "postgres://(?<user>.+?):(?<password>.+?)@(?<hostportdb>.*)".toRegex().matchEntire(url)?.groups ?: return
  Config["DB_URL"] = "jdbc:postgresql://${m["hostportdb"]!!.value}$suffix"
  Config["DB_USER"] = m["user"]!!.value
  Config["DB_PASS"] = m["password"]!!.value
}

/** Call this before to switch to less-privileged DB user after DB migration */
fun useAppDBUser(user: String = Config.optional("DB_APP_USER", "app"), password: String = Config["DB_APP_PASS"]) {
  Config["DB_USER"] = user
  Config["DB_PASS"] = password
}
