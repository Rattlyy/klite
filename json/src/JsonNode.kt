@file:Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
package klite.json

import klite.d

typealias JsonNode = Map<String, Any?>
typealias JsonList = List<JsonNode>

@kotlin.internal.HidesMembers
inline fun <T> JsonNode.get(key: String) = get(key) as T
inline fun <T> JsonNode.getOrNull(key: String) = get(key) as T?

inline fun JsonNode.getString(key: String) = get<String>(key)
inline fun JsonNode.getInt(key: String) = get<Number>(key).toInt()
inline fun JsonNode.getLong(key: String) = get<Number>(key).toLong()
inline fun JsonNode.getBigDecimal(key: String) = get<String>(key).toBigDecimal()
inline fun JsonNode.getDecimal(key: String) = get<String>(key).d
inline fun <T> JsonNode.getList(key: String) = get<List<T>>(key).forEach {  }
inline fun <T> JsonNode.getMap(key: String) = get<Map<String, T>>(key)
inline fun JsonNode.getNode(key: String): JsonNode = getMap(key)
