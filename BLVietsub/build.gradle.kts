plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.lagradost.cloudstream3.gradle")
}

cloudstream {
    setPlugin(
        name = "BLVietsub",
        description = "Kho phim Đam Mỹ BLVietsub Mới Cập Nhật - Duy nhất nguồn BLVietsub chuẩn TV Box.",
        authors = listOf("BLVietsub"),
        types = listOf(
            com.lagradost.cloudstream3.TvType.TvSeries,
            com.lagradost.cloudstream3.TvType.Movie,
            com.lagradost.cloudstream3.TvType.AsianDrama
        ),
        iconUrl = "https://blvietsub.com/wp-content/uploads/2021/08/cropped-favicon-32x32.png",
        status = com.lagradost.cloudstream3.gradle.CloudstreamExtension.Status.Online
    )
}

android {
    namespace = "com.blvietsub"
}

dependencies {
    val cloudstreamApiVersion = "pre-release"
    compileOnly("com.lagradost:cloudstream3:$cloudstreamApiVersion")
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
}
