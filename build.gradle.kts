plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false

    // ✅ NECESSÁRIO para Compose com Kotlin 2.x
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}