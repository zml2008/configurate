plugins {
  id("org.spongepowered.configurate-component")
}

dependencies {
  api(project(":configurate-core"))
  api("com.fasterxml.jackson.core:jackson-core:2.10.1")
}
