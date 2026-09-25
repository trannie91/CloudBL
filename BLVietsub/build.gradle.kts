android {
    namespace = "com.blvietsub"
}

dependencies {
    // Thư viện Cloudstream core chính thức từ recloudstream
    val cloudstreamApiVersion = "-SNAPSHOT"
    compileOnly("com.github.recloudstream.cloudstream:library:$cloudstreamApiVersion")
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
}
