android {
    namespace = "com.blvietsub"
}

dependencies {
    // Thư viện Cloudstream core và plugins từ recloudstream
    val cloudstreamApiVersion = "-SNAPSHOT"
    implementation("com.github.recloudstream.cloudstream:library:$cloudstreamApiVersion")
    implementation("com.github.Blatzar:NiceHttp:0.4.11")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
}
