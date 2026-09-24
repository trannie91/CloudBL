package com.blvietsub

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class BLVietsubProvider : MainAPI() {
    override var mainUrl = "https://blvietsub.com"
    override var name = "BLVietsub"
    override val hasMainPage = true
    override var lang = "vi"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(
        TvType.TvSeries,
        TvType.Movie,
        TvType.AsianDrama
    )

    // Bỏ hết các nguồn khác, chỉ duy nhất danh mục của BLVietsub
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
        val items = doc.select("article").mapNotNull { article ->
            toSearchResult(article)
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = false
            ),
            hasNext = items.isNotEmpty()
        )
    }

    private fun toSearchResult(element: Element): SearchResponse? {
        val link = element.selectFirst("a[href^=https://blvietsub.com/]") ?: return null
        val href = fixUrl(link.attr("href"))
        val slug = href.removePrefix("https://blvietsub.com/").trim('/')
        
        // Bỏ qua các đường dẫn không phải phim
        if (slug.isEmpty() || listOf("category", "tag", "actor", "page", "xmlrpc", "feed", "wp-admin").contains(slug)) {
            return null
        }

        val title = element.selectFirst("h2, h3, h4")?.text()?.trim() 
            ?: link.attr("title").ifEmpty { slug }
            
        var poster = element.selectFirst("img")?.let { img ->
            img.attr("data-src").ifEmpty {
                img.attr("data-lazy-src").ifEmpty {
                    img.attr("src")
                }
            }
        } ?: ""
        
        // Lấy ảnh gốc chất lượng cao
        if (poster.contains(Regex("""-\d+x\d+\."""))) {
            poster = poster.replace(Regex("""-\d+x\d+\."""), ".")
        }

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.trim()}"
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document
        return doc.select("article").mapNotNull {
            toSearchResult(it)
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = mapOf("User-Agent" to userAgent)).document

        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        
        var poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("meta[name=twitter:image]")?.attr("content")
            ?: doc.selectFirst("article img")?.attr("src")
            ?: ""
        
        if (poster.contains(Regex("""-\d+x\d+\."""))) {
            poster = poster.replace(Regex("""-\d+x\d+\."""), ".")
        }

        val description = doc.select("article p")
            .filter { it.text().length > 20 && !it.text().contains("Xem phim") && !it.text().contains("Server SS") }
            .joinToString("\n\n") { it.text().trim() }
            .ifEmpty { title }

        val year = Regex("""\((\d{4})\)""").find(title)?.groupValues?.get(1)?.toIntOrNull()
        val tags = doc.select("article a[href*=/category/]").map { it.text().trim() }

        // Bóc tách danh sách tập phim từ data-server-url
        val episodes = mutableListOf<Episode>()
        val serverButtons = doc.select("[data-server-url]")

        if (serverButtons.isNotEmpty()) {
            serverButtons.forEachIndexed { index, btn ->
                val serverUrl = btn.attr("data-server-url").trim()
                val label = btn.attr("data-server-label").ifEmpty { btn.text().trim() }
                val epNum = Regex("""\d+""").find(label)?.value?.toIntOrNull() ?: (index + 1)
                
                episodes.add(
                    Episode(
                        data = serverUrl,
                        name = label.ifEmpty { "Tập $epNum" },
                        episode = epNum
                    )
                )
            }
        } else {
            // Fallback iframe
            val iframes = doc.select("article iframe")
            iframes.forEachIndexed { index, iframe ->
                val src = iframe.attr("src").trim()
                if (src.isNotEmpty()) {
                    episodes.add(
                        Episode(
                            data = src,
                            name = "Tập ${index + 1}",
                            episode = index + 1
                        )
                    )
                }
            }
        }

        val distinctEpisodes = episodes.distinctBy { it.data }

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
        return loadExtractor(data, subtitleCallback, callback)
    }
}
