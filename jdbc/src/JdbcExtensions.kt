package klite.jdbc

import org.intellij.lang.annotations.Language
import java.io.InputStream
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.sql.Statement.RETURN_GENERATED_KEYS
import java.time.Period
import java.util.*
import javax.sql.DataSource
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.typeOf

val namesToQuote = mutableSetOf("limit", "offset", "check", "table", "column", "constraint", "default", "desc", "distinct", "end", "foreign", "from", "grant", "group", "primary", "user")

typealias Column = Any // String | KProperty1
typealias Where = Map<out Column, Any?>

fun <R, ID> DataSource.query(table: String, id: ID, mapper: ResultSet.() -> R): R =
  query(table, mapOf("id" to id), into = ArrayList(1), mapper = mapper).firstOrNull() ?: throw NoSuchElementException("$table:$id not found")

fun <R> DataSource.query(table: String, where: Where, suffix: String = "", into: MutableCollection<R> = mutableListOf(), mapper: ResultSet.() -> R): Collection<R> =
  select("select * from $table", where, suffix, into, mapper)

inline fun <reified R: Any> DataSource.query(table: String, where: Where = emptyMap(), suffix: String = "", noinline mapper: ResultSet.() -> R = { fromValues() }): List<R> =
  query(table, where, suffix, mutableListOf(), mapper) as List<R>

fun <R> DataSource.select(@Language("SQL") select: String, where: Where, suffix: String = "", into: MutableCollection<R>, mapper: ResultSet.() -> R): MutableCollection<R> =
  withStatement("$select${where.expr} $suffix") {
    setAll(whereValues(where))
    into.also { executeQuery().process(it::add, mapper) }
  }

// backwards-compatibility
fun <R> DataSource.select(@Language("SQL") select: String, where: Where, suffix: String = "", mapper: ResultSet.() -> R): List<R> =
  select(select, where, suffix, mutableListOf(), mapper) as List<R>

internal inline fun <R> ResultSet.process(consumer: (R) -> Unit = {}, mapper: ResultSet.() -> R) {
  while (next()) consumer(mapper())
}

fun DataSource.exec(@Language("SQL") expr: String, values: Sequence<Any?> = emptySequence(), callback: (Statement.() -> Unit)? = null): Int =
  withStatement(expr) {
    setAll(values)
    executeUpdate().also {
      if (callback != null) callback()
    }
  }

fun <R> DataSource.withStatement(@Language("SQL") sql: String, block: PreparedStatement.() -> R): R = withConnection {
  try {
    prepareStatement(sql, RETURN_GENERATED_KEYS).use { it.block() }
  } catch (e: SQLException) {
    throw if (e.message?.contains("unique constraint") == true) AlreadyExistsException(e)
          else SQLException(e.message + "\nSQL: $sql", e.sqlState, e.errorCode, e)
  }
}

fun DataSource.insert(table: String, values: Map<String, *>): Int {
  val valuesToSet = values.filter { it.value !is GeneratedKey<*> }
  return exec(insertExpr(table, valuesToSet), setValues(valuesToSet)) {
    if (valuesToSet.size != values.size) processGeneratedKeys(values)
  }
}

fun DataSource.upsert(table: String, values: Map<String, *>, uniqueFields: String = "id"): Int =
  exec(insertExpr(table, values) + " on conflict ($uniqueFields) do update set ${setExpr(values)}", setValues(values) + setValues(values))

private fun insertExpr(table: String, values: Map<String, *>) = """
  insert into $table (${values.keys.joinToString { q(it) }})
    values (${values.entries.joinToString { (it.value as? SqlExpr)?.expr(it.key) ?: "?" }})""".trimIndent()

fun DataSource.update(table: String, where: Where, values: Map<String, *>): Int =
  exec("update $table set ${setExpr(values)}${where.expr}", setValues(values) + whereValues(where))

fun DataSource.delete(table: String, where: Where): Int =
  exec("delete from $table${where.expr}", whereValues(where))

private fun setExpr(values: Map<String, *>) = values.entries.joinToString { q(it.key) + " = " + ((it.value as? SqlExpr)?.expr(it.key) ?: "?") }

private val Where.expr get() = if (isEmpty()) "" else " where " + join(" and ")

private fun Where.join(separator: String) = entries.joinToString(separator) { (k, v) ->
  val n = name(k)
  when (v) {
    null -> q(n) + " is null"
    is SqlExpr -> v.expr(n)
    is Iterable<*> -> inExpr(n, v)
    is Array<*> -> inExpr(n, v.toList())
    else -> q(n) + " = ?"
  }
}

fun or(vararg where: Pair<Any, Any?>) = where.toMap().let { SqlExpr("(" + it.join(" or ") + ")", whereValues(it).toList()) }

private fun name(key: Any) = when(key) {
  is KProperty1<*, *> -> key.name
  is String -> key
  else -> throw UnsupportedOperationException("$key should be a KProperty1 or String")
}

internal fun q(name: String) = if (name in namesToQuote) "\"$name\"" else name

internal fun inExpr(k: String, v: Iterable<*>) = q(k) + " in (${v.joinToString { "?" }})"

private fun setValues(values: Map<String, Any?>) = values.values.asSequence().flatMap { it.flatExpr() }
private fun Any?.flatExpr(): Iterable<Any?> = if (this is SqlExpr) values else listOf(this)

private fun whereValues(where: Map<*, Any?>) = where.values.asSequence().filterNotNull().flatMap { it.toIterable() }
private fun Any?.toIterable(): Iterable<Any?> = when (this) {
  is Array<*> -> toList()
  is Iterable<*> -> this
  else -> flatExpr()
}

operator fun PreparedStatement.set(i: Int, value: Any?) {
  if (value is InputStream) setBinaryStream(i, value)
  else setObject(i, JdbcConverter.to(value, connection))
}
fun PreparedStatement.setAll(values: Sequence<Any?>) = values.forEachIndexed { i, v -> this[i + 1] = v }

@Suppress("UNCHECKED_CAST")
fun <T> ResultSet.get(column: String, type: KType): T = JdbcConverter.from(getObject(column), type) as T
inline operator fun <reified T> ResultSet.get(column: String): T = get(column, typeOf<T>())

fun ResultSet.getId(column: String = "id") = getString(column).toId()
fun ResultSet.getIdOrNull(column: String) = getString(column)?.toId()
fun ResultSet.getInstant(column: String) = getTimestamp(column).toInstant()
fun ResultSet.getInstantOrNull(column: String) = getTimestamp(column)?.toInstant()
fun ResultSet.getLocalDate(column: String) = getDate(column).toLocalDate()
fun ResultSet.getLocalDateOrNull(column: String) = getDate(column)?.toLocalDate()
fun ResultSet.getPeriod(column: String) = Period.parse(getString(column))
fun ResultSet.getPeriodOrNull(column: String) = getString(column)?.let { Period.parse(it) }
fun ResultSet.getIntOrNull(column: String) = getObject(column)?.let { (it as Number).toInt() }

fun String.toId(): UUID = UUID.fromString(this)

inline fun <reified T: Enum<T>> ResultSet.getEnum(column: String) = enumValueOf<T>(getString(column))
inline fun <reified T: Enum<T>> ResultSet.getEnumOrNull(column: String) = getString(column)?.let { enumValueOf<T>(it) }
