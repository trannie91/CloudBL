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
            
        var poster = element.selectFirst("img")?.let { img ->
            img.attr("data-src").ifEmpty {
                img.attr("data-lazy-src").ifEmpty {
                    img.attr("src")
                }
            }
        } ?: ""
        
        // Lay anh goc chat luong cao
        if (poster.contains(Regex("""-\d+x\d+\."""))) {
            poster = poster.replace(Regex("""-\d+x\d+\."""), ".")
        }

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

        // Bóc tách danh sách tập phim - ƯU TIÊN VÀ CHỈ LỌC DUY NHẤT SERVER DL
        val episodes = mutableListOf<Episode>()
        val allServerButtons = doc.select("[data-server-url], .server-item button, .server-item a, .list-server a, .btn-episode, [data-server]")

        // Lọc danh sách riêng cho SERVER DL
        val dlButtons = allServerButtons.filter { btn ->
            val text = btn.text()
            val serverName = btn.attr("data-server-name")
            val serverLabel = btn.attr("data-server-label")
            val dataServer = btn.attr("data-server")
            val dataUrl = btn.attr("data-server-url")
            val parentText = btn.parent()?.text() ?: ""
            val grandParentText = btn.parent()?.parent()?.text() ?: ""

            serverName.equals("DL", ignoreCase = true) ||
            serverLabel.contains("DL", ignoreCase = true) ||
            dataServer.equals("DL", ignoreCase = true) ||
            dataServer.contains("DL", ignoreCase = true) ||
            text.contains("DL", ignoreCase = true) ||
            parentText.contains("Server DL", ignoreCase = true) ||
            grandParentText.contains("Server DL", ignoreCase = true) ||
            dataUrl.contains("/dl/", ignoreCase = true)
        }

        val targetButtons = if (dlButtons.isNotEmpty()) {
            dlButtons
        } else {
            val nonSSandHX = allServerButtons.filter { btn ->
                val combined = "${btn.text()} ${btn.attr("data-server-name")} ${btn.attr("data-server-label")} ${btn.attr("data-server")}"
                !combined.contains("SS", ignoreCase = true) && !combined.contains("HX", ignoreCase = true)
            }
            if (nonSSandHX.isNotEmpty()) nonSSandHX else allServerButtons
        }

        if (targetButtons.isNotEmpty()) {
            targetButtons.forEachIndexed { index, btn ->
                val serverUrl = btn.attr("data-server-url").ifEmpty {
                    btn.attr("data-url").ifEmpty {
                        btn.attr("href")
                    }
                }.trim()

                if (serverUrl.isNotEmpty() && !serverUrl.startsWith("#") && !serverUrl.startsWith("javascript")) {
                    val rawLabel = btn.attr("data-server-label").ifEmpty { btn.text().trim() }
                    val epNum = Regex("""\d+""").find(rawLabel)?.value?.toIntOrNull() ?: (index + 1)
                    val cleanName = if (rawLabel.contains(Regex("""(?i)Tập\s*\d+"""))) {
                        Regex("""(?i)Tập\s*\d+""").find(rawLabel)?.value ?: "Tập $epNum"
                    } else {
                        "Tập $epNum"
                    }

                    episodes.add(
                        newEpisode(serverUrl) {
                            this.name = cleanName
                            this.episode = epNum
                        }
                    )
                }
            }
        } else {
            // Fallback iframe
            val iframes = doc.select("article iframe, .film-player iframe")
            iframes.forEachIndexed { index, iframe ->
                val src = iframe.attr("src").trim()
                if (src.isNotEmpty()) {
                    episodes.add(
                        newEpisode(src) {
                            this.name = "Tập ${index + 1}"
                            this.episode = index + 1
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Tự động sử dụng SSPlayExtractor cho luồng video từ máy chủ SSPlay
        if (data.contains("ssplay.net") || data.contains("blvietsub")) {
            val extractor = SSPlayExtractor()
            extractor.getUrl(data, "$mainUrl/", subtitleCallback, callback)
            return true
        }

        // Tự động sử dụng các Extractor khác đã tích hợp sẵn trong Cloudstream
        return loadExtractor(data, "$mainUrl/", subtitleCallback, callback)
    }
}
