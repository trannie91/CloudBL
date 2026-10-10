package com.blvietsub

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink

class SSPlayExtractor : ExtractorApi() {
    override val name = "SSPlay"
    override val mainUrl = "https://ssplay.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val videoId = Regex("""/v/([a-zA-Z0-9]+)""").find(url)?.groupValues?.get(1)
            ?: Regex("""([a-zA-Z0-9]+)\.html""").find(url)?.groupValues?.get(1)

        val headers = mapOf(
            "Referer" to (referer ?: "https://blvietsub.com/"),
            "Origin" to "https://blvietsub.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )

        try {
            val response = app.get(url, headers = headers).text

            // 1. Trich xuat chinh xac theo co che may chu SSPlay: html|{id}|{tag}|...|SU
            if (videoId != null) {
                val marker = "html|$videoId|"
                val pos = response.indexOf(marker)
                if (pos != -1) {
                    val tag = response.substring(pos + marker.length).substringBefore("|").trim()
                    if (tag.isNotEmpty()) {
                        val streamUrl = "https://ssplay.net/SU/$tag/$videoId.html"
                    callback.invoke(
                        newExtractorLink(
                            source = name,
                            name = "$name - 1080p FHD",
                            url = streamUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "https://ssplay.net/"
                            this.headers = mapOf(
                                "Referer" to "https://ssplay.net/",
                                "Origin" to "https://ssplay.net",
                                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            )
                            this.quality = Qualities.P1080.value
                        }
                    )
                    return
                }
            }

            // 2. Fallback tim /SU/ truc tiep
            val directSuMatch = Regex("""["'](/SU/[^"']+)["']""").find(response)?.groupValues?.get(1)
                ?: Regex("""/SU/[a-zA-Z0-9_\-./]+""").find(response)?.value

            if (directSuMatch != null) {
                val streamUrl = if (directSuMatch.startsWith("http")) directSuMatch else "https://ssplay.net$directSuMatch"
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name - 1080p FHD",
                        url = streamUrl,
                        type = ExtractorLinkType.M3U8
                    ) {
                        this.referer = "https://ssplay.net/"
                        this.headers = mapOf(
                            "Referer" to "https://ssplay.net/",
                            "Origin" to "https://ssplay.net",
                            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                        )
                        this.quality = Qualities.P1080.value
                    }
                )
            }
        } catch (e: Exception) {}
    }
}
