dependencies {
  api(project(":server"))
  api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.4")
  api("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.4") {
    exclude("org.jetbrains.kotlin")
  }
}
