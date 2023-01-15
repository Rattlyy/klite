package klite.jdbc

import klite.*
import klite.jdbc.ChangeSet.On.*
import java.sql.SQLException
import javax.sql.DataSource

/**
 * Applies changesets to the DB that haven't been applied yet, a simpler Liquibase replacement.
 * TODO: locking during run
 */
open class DBMigrator(
  private val db: DataSource = ConfigDataSource(),
  private val changeSets: Sequence<ChangeSet> = ChangeSetFileReader(Config.optional("DB_MIGRATE", "db.sql")),
  private val contexts: Set<String> = Config.active,
  private val dropAllOnFailure: Boolean = Config.isTest
): Extension {
  private val log = logger()
  private val repository = ChangeSetRepository(db)

  private val tx = Transaction(db)
  private var history: Map<String, ChangeSet> = emptyMap()

  override fun install(server: Server) = migrate()

  fun migrate() = try {
    doMigrate()
  } catch (e: MigrationException) {
    if (dropAllOnFailure) {
      log.error("Migration failed, dropping and retrying from scratch", e)
      dropAll()
      doMigrate()
    } else throw e
  }

  private fun doMigrate() = tx.attachToThread().use {
    history = readHistory()
    changeSets.forEach(::run)
  }

  fun dropAll(schema: String? = null) = tx.attachToThread().use {
    val schema = schema ?: db.withConnection { this.schema }
    log.warn("Dropping and recreating schema $schema")
    db.exec("drop schema $schema cascade")
    db.exec("create schema $schema")
  }

  private fun readHistory() = (try { repository.list() } catch (e: SQLException) {
    tx.rollback()
    ChangeSetFileReader("migrator/init.sql").forEach(::run)
    repository.list()
  }).associateBy { it.id }

  fun run(changeSet: ChangeSet) {
    changeSet.finish()
    if (changeSet.id.isEmpty()) {
      if (changeSet.sql.isNotEmpty())
        throw MigrationException("Cannot run dangling SQL without a changeset:\n" + changeSet.sql)
      return
    }
    if (!changeSet.matches(contexts)) return

    var run = true
    history[changeSet.id]?.let {
      if (it.checksum == changeSet.checksum) return
      if (it.checksum == null) {
        log.info("Storing new checksum for ${changeSet.id}")
        return markRan(changeSet)
      }
      when (changeSet.onChange) {
        FAIL -> throw MigrationException(changeSet, "has changed, old checksum=${it.checksum}, use onChange to override")
        SKIP -> return log.warn("Skipping changed $changeSet")
        MARK_RAN -> {
          log.warn("$changeSet - marking as ran")
          run = false
        }
        RUN -> {}
      }
    }

    try {
      if (run) changeSet.statements.forEach {
        log.info("Running ${changeSet.copy(sql = it)}")
        changeSet.rowsAffected += db.exec(it)
      }
      markRan(changeSet)
    } catch (e: Exception) {
      tx.rollback()
      when (changeSet.onFail) {
        SKIP -> log.warn("Skipping failed $changeSet: $e")
        MARK_RAN -> {
          log.warn("Marking as ran failed $changeSet: $e")
          markRan(changeSet)
        }
        else -> throw MigrationException(changeSet, e)
      }
    }
  }

  private fun markRan(changeSet: ChangeSet) {
    repository.save(changeSet)
    tx.commit()
  }
}

class MigrationException(message: String, cause: Throwable? = null): RuntimeException(message, cause) {
  constructor(changeSet: ChangeSet, cause: Throwable? = null): this("Failed $changeSet", cause)
  constructor(changeSet: ChangeSet, message: String): this("$changeSet\n - $message")
}
