package com.blvietsub

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
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
        val ref = referer ?: "https://blvietsub.com/"
        val headers = mapOf(
            "Referer" to ref,
            "Origin" to "https://blvietsub.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Accept" to "*/*"
        )

        try {
            val response = app.get(url, headers = headers).text

            fun emitLink(linkUrl: String, qualityLabel: String = "1080p FHD") {
                val streamUrl = if (linkUrl.startsWith("http")) linkUrl else "https://ssplay.net$linkUrl"
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = "$name - $qualityLabel",
                        url = streamUrl,
                        type = if (streamUrl.contains(".mp4")) ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
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

            // 1. Tim duong dan /SU/ truc tiep trong HTML
            val directSuMatch = Regex("""["'](/SU/[^"']+)["']""").find(response)?.groupValues?.get(1)
                ?: Regex("""/SU/[a-zA-Z0-9_\-./]+""").find(response)?.value
            if (directSuMatch != null) {
                emitLink(directSuMatch)
                return
            }

            // 2. Tim link .m3u8 truc tiep
            val m3u8Match = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(response)?.value
            if (m3u8Match != null) {
                emitLink(m3u8Match)
                return
            }

            // 3. Tim link mp4
            val mp4Match = Regex("""https?://[^\s"']+\.mp4[^\s"']*""").find(response)?.value
            if (mp4Match != null) {
                emitLink(mp4Match)
                return
            }

            // 4. Tim trong file/source cua player
            val fileRegex = Regex("""["']?(?:file|source|sources|hls)["']?\s*:\s*["']([^"']+)["']""")
            val fileMatch = fileRegex.find(response)?.groupValues?.get(1)
            if (!fileMatch.isNullOrEmpty() && (fileMatch.contains(".m3u8") || fileMatch.contains("/SU/") || fileMatch.contains(".mp4"))) {
                emitLink(fileMatch)
                return
            }

            // 5. Giai ma Packer
            if (response.contains("eval(function(p,a,c,k,e,")) {
                try {
                    val unpacked = getAndUnpack(response)
                    val suMatch = Regex("""["'](/SU/[^"']+)["']""").find(unpacked)?.groupValues?.get(1)
                        ?: Regex("""/SU/[a-zA-Z0-9_\-./]+""").find(unpacked)?.value
                    if (suMatch != null) {
                        emitLink(suMatch)
                        return
                    }

                    val unpackedM3u8 = Regex("""https?://[^\s"']+\.m3u8[^\s"']*""").find(unpacked)?.value
                    if (unpackedM3u8 != null) {
                        emitLink(unpackedM3u8)
                        return
                    }

                    val unpackedFile = fileRegex.find(unpacked)?.groupValues?.get(1)
                    if (!unpackedFile.isNullOrEmpty() && (unpackedFile.contains(".m3u8") || unpackedFile.contains("/SU/") || unpackedFile.contains(".mp4"))) {
                        emitLink(unpackedFile)
                        return
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
