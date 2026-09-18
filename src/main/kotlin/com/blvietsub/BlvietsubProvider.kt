package com.blvietsub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class BlvietsubProvider : MainAPI() {
    override var mainUrl = "https://blvietsub.com"
    override var name = "BLVietsub"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.TvSeries,
        TvType.Movie
    )

    // 1. Toàn bộ 8 danh mục phim đồng bộ 100% với Stremio Addon
    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Mới Cập Nhật",
        "$mainUrl/category/phim-bo/page/" to "Phim Bộ",
        "$mainUrl/category/phim-le/page/" to "Phim Lẻ",
        "$mainUrl/category/hoan-tat/page/" to "Hoàn Tất",
        "$mainUrl/category/thai-lan/page/" to "Thái Lan",
        "$mainUrl/category/trung-quoc/page/" to "Trung Quốc",
        "$mainUrl/category/han-quoc/page/" to "Hàn Quốc",
        "$mainUrl/category/nhat-ban/page/" to "Nhật Bản"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data}$page/"
        val document = app.get(url, referer = mainUrl).document
        val home = document.select("a[href*='blvietsub.com']").mapNotNull { element ->
            toSearchResult(element)
        }.distinctBy { it.url }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = home.isNotEmpty()
        )
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val href = element.attr("href")
        if (href.contains("/category/") || href.contains("/tag/") || href.contains("/page/")) return null
        
        val title = element.selectFirst("img")?.attr("alt") 
            ?: element.selectFirst("img")?.attr("title") 
            ?: element.text()
        if (title.isBlank() || (title.contains("xem phim", ignoreCase = true) && title.length > 50)) return null

        val posterUrl = element.selectFirst("img")?.attr("src")
            ?.replace(Regex("-\\d+x\\d+\\."), ".")

        return newTvSeriesSearchResponse(title.trim(), href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    // 2. Tìm kiếm phim chính xác
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.trim().replace(" ", "+")}"
        val document = app.get(searchUrl, referer = mainUrl).document
        return document.select("a[href*='blvietsub.com']").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    // 3. Chi tiết phim và toàn bộ danh sách tập
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url, referer = mainUrl).document

        val title = document.selectFirst("h1")?.text()?.trim() ?: "BLVietsub Video"
        val yearMatch = Regex("\\((\\d{4})\\)").find(title)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()

        val poster = document.select("img").map { it.attr("src") }
            .firstOrNull { src ->
                !src.contains("logo") && !src.contains("avatar") && !src.contains("banner")
            }?.replace(Regex("-\\d+x\\d+\\."), ".")

        val synopsis = document.select("p").filter {
            val txt = it.text()
            txt.length > 20 && !txt.contains("Xem phim BL") && !txt.contains("bình luận")
        }.joinToString("\n\n") { it.text().trim() }

        val episodes = mutableListOf<Episode>()
        val serverButtons = document.select("[data-server-url]")
        
        if (serverButtons.isNotEmpty()) {
            serverButtons.forEachIndexed { index, btn ->
                val epUrl = btn.attr("data-server-url")
                val epLabel = btn.attr("data-server-label").ifBlank { "Tập ${index + 1}" }
                val epNum = Regex("(\\d+)").find(epLabel)?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)

                episodes.add(
                    Episode(
                        data = epUrl,
                        name = epLabel,
                        episode = epNum,
                        season = 1
                    )
                )
            }
        } else {
            episodes.add(
                Episode(
                    data = url,
                    name = "Full Phim",
                    episode = 1,
                    season = 1
                )
            )
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year = year
            this.plot = synopsis
            this.tags = listOf("Đam Mỹ", "BL Vietsub", "Phim Châu Á")
        }
    }

    // 4. Bóc tách link Stream 1080p HLS (SSPlay & Dailymotion)
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Nguồn 1: SSPlay (ssplay.net)
        if (data.contains("ssplay.net")) {
            val response = app.get(data, referer = mainUrl).text
            
            // Tìm link trực tiếp dạng /SU/
            val directMatch = Regex("["'](/SU/[^"']+)["']").find(response)
            if (directMatch != null) {
                val directUrl = "https://ssplay.net" + directMatch.groupValues[1]
                callback(
                    ExtractorLink(
                        source = this.name,
                        name = "SSPlay 1080p Direct HLS",
                        url = directUrl,
                        referer = "https://ssplay.net/",
                        quality = Qualities.P1080.value,
                        isM3u8 = true
                    )
                )
                return true
            }

            // Giải mã Packer JS nếu bị nén eval(function(p,a,c,k,e,d)...)
            val packedJs = getPacked(response)
            if (packedJs != null) {
                val suMatch = Regex("["'](/SU/[^"']+)["']").find(packedJs)
                if (suMatch != null) {
                    val streamUrl = "https://ssplay.net" + suMatch.groupValues[1]
                    callback(
                        ExtractorLink(
                            source = this.name,
                            name = "SSPlay VIP 1080p HLS",
                            url = streamUrl,
                            referer = "https://ssplay.net/",
                            quality = Qualities.P1080.value,
                            isM3u8 = true
                        )
                    )
                    return true
                }
            }
        }

        // Nguồn 2: Dailymotion
        if (data.contains("dailymotion.com")) {
            val videoId = Regex("/(?:video|embed/video)/([a-zA-Z0-9]+)").find(data)?.groupValues?.get(1)
            if (videoId != null) {
                val metaJson = app.get("https://www.dailymotion.com/player/metadata/video/$videoId").parsedSafe<DailymotionMeta>()
                val autoUrl = metaJson?.qualities?.auto?.firstOrNull()?.url
                if (autoUrl != null) {
                    callback(
                        ExtractorLink(
                            source = "Dailymotion",
                            name = "Dailymotion 1080p HLS",
                            url = autoUrl,
                            referer = "https://www.dailymotion.com/",
                            quality = Qualities.P1080.value,
                            isM3u8 = true
                        )
                    )
                    return true
                }
            }
        }

        return false
    }

    data class DailymotionMeta(
        val qualities: DailymotionQualities? = null
    )
    data class DailymotionQualities(
        val auto: List<DailymotionAutoQuality>? = null
    )
    data class DailymotionAutoQuality(
        val url: String? = null
    )
}
