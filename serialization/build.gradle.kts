plugins {
  kotlin("plugin.serialization") version "1.8.21"
}

dependencies {
  api(project(":server"))
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.kotlinx.datetime)
}
