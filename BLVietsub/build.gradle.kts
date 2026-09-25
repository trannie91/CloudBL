android {
    namespace = "com.blvietsub"
}

dependencies {
    val cloudstreamApiVersion = "master-SNAPSHOT"
    compileOnly("com.github.recloudstream.cloudstream:library:$cloudstreamApiVersion")
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
}
