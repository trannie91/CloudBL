package com.blvietsub

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack

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
        val ref = referer ?: "https://blvietsub.com/"
        val headers = mapOf(
            "Referer" to ref,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        )

        val response = app.get(url, headers = headers).text

        // 1. Tìm đường dẫn /SU/ trực tiếp trong HTML
        val directSuMatch = Regex("""["'](/SU/[^"']+)["']""").find(response)?.groupValues?.get(1)
            ?: Regex("""/SU/[a-zA-Z0-9_\-./]+""").find(response)?.value

        if (directSuMatch != null) {
            val streamUrl = if (directSuMatch.startsWith("http")) directSuMatch else "https://ssplay.net$directSuMatch"
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "$name - 1080p FHD",
                    url = streamUrl,
                    referer = "https://ssplay.net/",
                    quality = Qualities.P1080.value,
                    isM3u8 = true
                )
            )
            return
        }

        // 2. Tìm link .m3u8 trực tiếp
        val m3u8Match = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(response)?.value
        if (m3u8Match != null) {
            callback.invoke(
                ExtractorLink(
                    source = name,
                    name = "$name - 1080p FHD",
                    url = m3u8Match,
                    referer = "https://ssplay.net/",
                    quality = Qualities.P1080.value,
                    isM3u8 = true
                )
            )
            return
        }

        // 3. Giải mã Dean Edwards Packer nếu trang bị obfuscate
        if (response.contains("eval(function(p,a,c,k,e,d)")) {
            try {
                val unpacked = getAndUnpack(response)
                val suMatch = Regex("""["'](/SU/[^"']+)["']""").find(unpacked)?.groupValues?.get(1)
                    ?: Regex("""/SU/[a-zA-Z0-9_\-./]+""").find(unpacked)?.value

                if (suMatch != null) {
                    val finalUrl = if (suMatch.startsWith("http")) suMatch else "https://ssplay.net$suMatch"
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "$name - 1080p FHD",
                            url = finalUrl,
                            referer = "https://ssplay.net/",
                            quality = Qualities.P1080.value,
                            isM3u8 = true
                        )
                    )
                    return
                }

                val fileMatch = Regex("""["']file["']\s*:\s*["']([^"']+)["']""").find(unpacked)?.groupValues?.get(1)
                if (fileMatch != null && (fileMatch.contains(".m3u8") || fileMatch.contains("/SU/"))) {
                    val finalUrl = if (fileMatch.startsWith("http")) fileMatch else "https://ssplay.net$fileMatch"
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "$name - 1080p FHD",
                            url = finalUrl,
                            referer = "https://ssplay.net/",
                            quality = Qualities.P1080.value,
                            isM3u8 = true
                        )
                    )
                }
            } catch (e: Exception) {
                // Ignore unpacking errors
            }
        }
    }
}
