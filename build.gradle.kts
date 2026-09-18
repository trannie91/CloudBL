import com.lagradost.cloudstream3.gradle.CloudstreamExtension

buildscript {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("com.github.recloudstream:gradle:master-SNAPSHOT")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    }
}

apply(plugin = "com.android.library")
apply(plugin = "kotlin-android")
apply(plugin = "cloudstream")

configure<com.android.build.gradle.LibraryExtension> {
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

configure<CloudstreamExtension> {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/trannie91/CloudBL")
    authors = listOf("BLVietsub")
    description = "Xem phim Đam Mỹ BLVietsub mượt mà trên Cloudstream Android TV & Mobile"
}

dependencies {
    val cloudstreamApiVersion = "pre-release"
    "implementation"("com.github.recloudstream:cloudstream:$cloudstreamApiVersion")
    "implementation"("org.jsoup:jsoup:1.17.2")
}
