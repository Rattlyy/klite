package klite.openapi

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import klite.*
import klite.StatusCode.Companion.OK
import klite.annotations.*
import java.math.BigDecimal
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.jvmName

// Spec: https://swagger.io/specification/
// Sample: https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/api-with-examples.json

/**
 * Adds an /openapi endpoint to the context, describing all the routes.
 * Use @Operation swagger annotations to describe the routes.
 * @Parameter swagger annotation can be used on method parameters directly.
 *
 * To run Swagger-UI:
 *   add `before(CorsHandler())`
 *   `docker run -d -p 8080:8088 -e SWAGGER_JSON_URL=http://YOUR-IP/api/openapi swaggerapi/swagger-ui`
 *   Open http://localhost:8088
 */
fun Router.openApi(path: String = "/openapi", title: String = "API", version: String = "1.0.0") {
  get(path) {
    mapOf(
      "openapi" to "3.0.0",
      "info" to mapOf("title" to title, "version" to version),
      "servers" to listOf(mapOf("url" to fullUrl(prefix))),
      "paths" to routes.groupBy { pathParamRegexer.toOpenApi(it.path) }.mapValues { (_, routes) ->
        routes.associate { route ->
          val op = route.annotation<Operation>()
          (op?.method?.trimToNull() ?: route.method.name).lowercase() to mapOf(
            "operationId" to route.handler.let { (if (it is FunHandler) it.instance::class.simpleName + "." + it.f.name else it::class.simpleName) },
            "parameters" to (route.handler as? FunHandler)?.let { it.params.filter { it.source != null }.map { p -> mapOf(
              "name" to p.name,
              "required" to (!p.p.isOptional && !p.p.type.isMarkedNullable),
              "in" to toParameterIn(p.source),
              "schema" to toSchema(p.p.type.classifier),
            ) + ((p.p.findAnnotation<Parameter>() ?: op?.parameters?.find { it.name == p.name })?.toNonEmptyValues() ?: emptyMap()) } },
            "requestBody" to (route.handler as? FunHandler)?.params?.find { it.source == null && !(it.p.type.classifier as KClass<*>).jvmName.startsWith("klite") }?.p?.toRequestBody(),
            "responses" to mapOf(
              OK.value to mapOf("description" to "OK")
            )
          ) + (op?.let { it.toNonEmptyValues { it.name != "method" } + mapOf(
            "requestBody" to op.requestBody.toNonEmptyValues().takeIf { it.isNotEmpty() },
            "externalDocs" to op.externalDocs.toNonEmptyValues().takeIf { it.isNotEmpty() }
          ) } ?: emptyMap())
        }
      }
    )
  }
}

private fun toParameterIn(paramAnnotation: Annotation?) = when(paramAnnotation) {
  is HeaderParam -> ParameterIn.HEADER
  is QueryParam -> ParameterIn.QUERY
  is PathParam -> ParameterIn.PATH
  is CookieParam -> ParameterIn.COOKIE
  else -> null
}

private fun toSchema(type: KClassifier?) = mapOf("type" to when(type) {
  Boolean::class -> "boolean"
  Number::class -> "integer"
  BigDecimal::class, Decimal::class, Float::class, Double::class -> "float" // TODO: is this correct
  else -> "string"
}) // TODO: enum, object

private fun KParameter.toRequestBody() = mapOf("content" to mapOf("schema" to toSchema(type.classifier)))

private fun <T: Annotation> T.toNonEmptyValues(filter: (KProperty1<T, *>) -> Boolean = {true}) = toValues(publicProperties.filter(filter)).filterValues {
  it != "" && it != false && (it as? Array<*>)?.isEmpty() != true
}