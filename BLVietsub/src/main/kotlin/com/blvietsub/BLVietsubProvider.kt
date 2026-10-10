package com.blvietsub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class BLVietsubProvider : MainAPI() {
    override var mainUrl = "https://blvietsub.com"
    override var name = "BLVietsub"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.AsianDrama
    )

    // Chi duy nhat danh muc cua BLVietsub
    override val mainPage = mainPageOf(
        "" to "BLVietsub - Mới Cập Nhật",
        "category/phim-bo" to "Phim Bộ Đam Mỹ",
        "category/phim-le" to "Phim Lẻ Đam Mỹ",
        "category/thai-lan" to "BL Thái Lan",
        "category/trung-quoc" to "BL Trung Quốc",
        "category/han-quoc" to "BL Hàn Quốc",
        "category/nhat-ban" to "BL Nhật Bản",
        "category/dai-loan" to "BL Đài Loan",
        "category/hoan-tat" to "Phim Đã Hoàn Tất",
        "category/phim-doc" to "Phim Dọc"
    )

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private fun extractPoster(element: Element): String {
        val img = element.selectFirst("img")
        var poster = ""

        if (img != null) {
            val candidates = listOf(
                img.attr("data-src"),
                img.attr("data-lazy-src"),
                img.attr("data-original"),
                img.attr("data-srcset"),
                img.attr("srcset"),
                img.attr("src")
            )

            for (cand in candidates) {
                val trimmed = cand.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("data:image") && !trimmed.contains("blank.gif") && !trimmed.contains("placeholder")) {
                    poster = if (trimmed.contains(",")) {
                        trimmed.split(",").lastOrNull()?.trim()?.split(" ")?.firstOrNull() ?: ""
                    } else if (trimmed.contains(" ")) {
                        trimmed.split(" ").firstOrNull() ?: ""
                    } else {
                        trimmed
                    }
                    if (poster.isNotEmpty() && !poster.startsWith("data:image")) break
                }
            }
        }

        if (poster.isEmpty() || poster.startsWith("data:image")) {
            val styleEl = element.selectFirst("[style*='background-image'], [data-bg], [data-background]")
            val bgStyle = styleEl?.attr("style") ?: ""
            val bgMatch = Regex("""url\(['"]?(.*?)['"]?\)""").find(bgStyle)?.groupValues?.get(1)
            if (!bgMatch.isNullOrEmpty()) {
                poster = bgMatch
            } else {
                poster = styleEl?.attr("data-bg")?.ifEmpty { styleEl.attr("data-background") } ?: ""
            }
        }

        if (poster.isEmpty()) return ""
        return fixUrl(poster)
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = if (page <= 1) {
            if (request.data.isEmpty()) "$mainUrl/" else "$mainUrl/${request.data}/"
        } else {
            if (request.data.isEmpty()) "$mainUrl/page/$page/" else "$mainUrl/${request.data}/page/$page/"
        }

        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        val items = doc.select("article, .item-film, .halim-item, .film-item, .post-item").mapNotNull { article ->
            toSearchResult(article)
        }.distinctBy { it.url }

        return newHomePageResponse(
            listOf(
                HomePageList(
                    name = request.name,
                    list = items,
                    isHorizontalImages = false
                )
            ),
            hasNext = items.isNotEmpty()
        )
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val link = element.selectFirst("a[href^=https://blvietsub.com/]") 
            ?: element.selectFirst("a[href*='blvietsub.com']")
            ?: element.selectFirst("a") 
            ?: return null

        val href = fixUrl(link.attr("href"))
        val slug = href.removePrefix("https://blvietsub.com/").removePrefix("http://blvietsub.com/").trim('/')
        
        // Bo qua cac duong dan khong phai phim
        if (slug.isEmpty() || listOf("category", "tag", "actor", "page", "xmlrpc", "feed", "wp-admin", "lien-he", "privacy-policy").any { slug.startsWith(it) }) {
            return null
        }

        val title = element.selectFirst("h2, h3, h4, .entry-title, .title, .film-name")?.text()?.trim() 
            ?: link.attr("title").ifEmpty { slug.replace("-", " ") }
            
        val poster = extractPoster(element)

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = poster
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.trim().isEmpty()) return emptyList()

        val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val searchUrl = "$mainUrl/?s=$encodedQuery"
        
        val doc = app.get(searchUrl, headers = mapOf(
            "User-Agent" to userAgent,
            "Referer" to "$mainUrl/"
        )).document

        return doc.select("article, .item-film, .halim-item, .film-item, .post-item").mapNotNull {
            toSearchResult(it)
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document

        val title = doc.selectFirst("h1, .entry-title, .film-info h1")?.text()?.trim() ?: return null
        
        var poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")
            ?: doc.selectFirst("article img, .poster img")?.attr("src")
            ?: ""
        
        if (poster.contains(Regex("""-\d+x\d+\."""))) {
            poster = poster.replace(Regex("""-\d+x\d+\."""), ".")
        }

        val description = doc.select("article p, .film-content p, .entry-content p")
            .filter { it.text().length > 20 && !it.text().contains("Xem phim") && !it.text().contains("Server SS") && !it.text().contains("Server DL") && !it.text().contains("Server HX") }
            .joinToString("\n\n") { it.text().trim() }
            .ifEmpty { title }

        val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
        val tags = doc.select("article a[href*=/category/], .film-info a[href*=/category/]").map { it.text().trim() }

        // Bóc tách danh sách tập phim: Lấy cả Server SS và Server DL, loại bỏ Server HX
        val episodes = mutableListOf<Episode>()
        val allButtons = doc.select(".blv-server-button, [data-server-url], .server-item button, .server-item a")

        val ssMap = mutableMapOf<Int, String>()
        val dlMap = mutableMapOf<Int, String>()

        allButtons.forEachIndexed { idx, btn ->
            val u = btn.attr("data-server-url").ifEmpty { btn.attr("href") }.trim()
            val l = btn.attr("data-server-label").ifEmpty { btn.text() }.trim()

            if (u.isNotEmpty() && !u.startsWith("#") && !u.startsWith("javascript")) {
                val num = Regex("""\d+""").find(l)?.value?.toIntOrNull()
                    ?: Regex("""\d+""").find(btn.text())?.value?.toIntOrNull()
                    ?: (idx + 1)

                val isSS = l.contains("SS", ignoreCase = true) || u.contains("ssplay", ignoreCase = true)
                val isDL = l.contains("DL", ignoreCase = true) || u.contains("dailymotion", ignoreCase = true)

                if (isSS && !ssMap.containsKey(num)) {
                    ssMap[num] = u
                } else if (isDL && !dlMap.containsKey(num)) {
                    dlMap[num] = u
                }
            }
        }

        val allEpisodeNumbers = (ssMap.keys + dlMap.keys).sorted()

        if (allEpisodeNumbers.isNotEmpty()) {
            allEpisodeNumbers.forEach { num ->
                val ssUrl = ssMap[num] ?: ""
                val dlUrl = dlMap[num] ?: ""

                val episodePayload = "SS_DL::$ssUrl||$dlUrl"

                episodes.add(
                    newEpisode(episodePayload) {
                        this.name = "Tập $num"
                        this.episode = num
                    }
                )
            }
        } else {
            // Fallback: Loại trừ Server HX
            val nonHXButtons = allButtons.filter { btn ->
                val combined = "${btn.text()} ${btn.attr("data-server-label")} ${btn.attr("data-server-url")}"
                !combined.contains("HX", ignoreCase = true) && !combined.contains("abyssplayer", ignoreCase = true)
            }

            nonHXButtons.forEachIndexed { index, btn ->
                val u = btn.attr("data-server-url").ifEmpty { btn.attr("href") }.trim()
                if (u.isNotEmpty() && !u.startsWith("#") && !u.startsWith("javascript")) {
                    val epNum = Regex("""\d+""").find(btn.text())?.value?.toIntOrNull() ?: (index + 1)
                    episodes.add(
                        newEpisode(u) {
                            this.name = "Tập $epNum"
                            this.episode = epNum
                        }
                    )
                }
            }
        }

        val distinctEpisodes = episodes.distinctBy { it.data }.sortedBy { it.episode }

        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, distinctEpisodes) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
        }
    }

    private suspend fun extractDailymotionDirect(
        dmUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        try {
            val cleanUrl = dmUrl.substringBefore("?")
            val videoId = Regex("""(?:video|embed/video)/([a-zA-Z0-9]+)""").find(cleanUrl)?.groupValues?.get(1)
                ?: return false

            val apiUrl = "https://www.dailymotion.com/player/metadata/video/$videoId"
            val responseText = app.get(apiUrl, headers = mapOf(
                "User-Agent" to userAgent,
                "Referer" to "https://www.dailymotion.com/"
            )).text

            val domain = "cdndirector.dailymotion.com"
            val pos = responseText.indexOf(domain)
            if (pos != -1) {
                val urlStart = responseText.lastIndexOf("http", pos)
                val urlEnd = responseText.indexOf('"', pos)
                if (urlStart != -1 && urlEnd != -1) {
                    val rawUrl = responseText.substring(urlStart, urlEnd)
                    val streamUrl = rawUrl.replace("\", "")

                    callback.invoke(
                        newExtractorLink(
                            source = "Dailymotion",
                            name = "Dailymotion - 1080p FHD",
                            url = streamUrl,
                            type = ExtractorLinkType.M3U8
                        ) {
                            this.referer = "https://www.dailymotion.com/"
                            this.quality = Qualities.P1080.value
                        }
                    )
                    return true
                }
            }
        } catch (e: Exception) {}
        return false
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var hasLinks = false

        if (data.startsWith("SS_DL::")) {
            val payload = data.removePrefix("SS_DL::")
            val parts = payload.split("||")
            val ssUrl = parts.getOrNull(0)?.trim() ?: ""
            val dlUrl = parts.getOrNull(1)?.trim() ?: ""

            // 1. Nguồn SSPlay 1080p FHD (Ưu tiên)
            if (ssUrl.isNotEmpty()) {
                try {
                    val ssExtractor = SSPlayExtractor()
                    ssExtractor.getUrl(ssUrl, "$mainUrl/", subtitleCallback) { link ->
                        hasLinks = true
                        callback.invoke(link)
                    }
                } catch (e: Exception) {}
            }

            // 2. Nguồn Dailymotion 1080p FHD (Dự phòng song hành)
            if (dlUrl.isNotEmpty()) {
                try {
                    val dmSuccess = extractDailymotionDirect(dlUrl) { link ->
                        hasLinks = true
                        callback.invoke(link)
                    }
                    if (!dmSuccess) {
                        loadExtractor(dlUrl, "$mainUrl/", subtitleCallback) { link ->
                            hasLinks = true
                            callback.invoke(link)
                        }
                    }
                } catch (e: Exception) {}
            }

            return hasLinks
        }

        if (data.contains("ssplay.net") || data.contains("/ss/")) {
            val extractor = SSPlayExtractor()
            extractor.getUrl(data, "$mainUrl/", subtitleCallback, callback)
            return true
        }

        if (data.contains("dailymotion.com")) {
            val success = extractDailymotionDirect(data, callback)
            if (success) return true
        }

        return loadExtractor(data, "$mainUrl/", subtitleCallback, callback)
    }
}
