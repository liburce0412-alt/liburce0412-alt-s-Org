plugins {
  id("com.android.library")
}

if (System.getProperty("os.name").startsWith("Windows") && projectDir.absolutePath.any { it.code > 127 }) {
  layout.buildDirectory.set(file("${System.getProperty("user.home")}/.gradle/campusai-build/band-contract"))
}

android {
  namespace = "com.campusai.caesar.bandcontract"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 24
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  testImplementation(libs.junit)
}
