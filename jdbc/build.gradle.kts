dependencies {
  api(project(":core"))
  compileOnly(project(":server"))
  testImplementation(project(":server"))
  compileOnly(libs.postgresql)
  compileOnly(libs.hikari) {
    exclude("org.slf4j")
  }
}
