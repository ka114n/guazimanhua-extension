package eu.kanade.tachiyomi.extension.zh.guazimanhua

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class Guazimanhua : HttpSource() {

    override val supportsLatest = true

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    override fun headersBuilder() = super.headersBuilder()
        .add("User-Agent", userAgent)

    // ================================ Manga list ================================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/category.php?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseCatalog(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("category.php")
            .addQueryParameter("keyword", query)
            .addQueryParameter("page", page.toString())
            .apply {
                val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()
                if (genre != null && genre.state != 0) {
                    addQueryParameter("tag", genre.values[genre.state])
                }
            }
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseCatalog(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/update.php?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseCatalog(response)

    private fun parseCatalog(response: Response): MangasPage {
        val doc = response.asJsoup()
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1

        val mangas = doc.select("article.card, article.mobile-update-card").mapNotNull { card ->
            val link = card.selectFirst("a[href*='comic.php']") ?: return@mapNotNull null
            val id = link.attr("href").substringAfter("id=", "").substringBefore("&")
            if (id.isBlank()) return@mapNotNull null

            SManga.create().apply {
                url = link.attr("href")
                title = card.selectFirst("h2 a, h3 a")?.text()?.trim().orEmpty()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }

        val hasNextPage = doc.select("nav.pager a[href]").any { link ->
            link.attr("href").contains("page=${currentPage + 1}")
        }
        return MangasPage(mangas, hasNextPage)
    }

    // ================================ Manga details ================================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        val manga = SManga.create()

        manga.url = buildString {
            append("/comic.php?id=")
            append(response.request.url.queryParameter("id"))
        }
        manga.title = doc.selectFirst(".mobile-comic-title")?.text()?.trim().orEmpty()
        manga.thumbnail_url = doc.selectFirst("img.mobile-comic-cover")?.absUrl("src")
        manga.genre = doc.selectFirst(".mobile-comic-tags")?.text()?.trim()
        manga.description = doc.selectFirst(".mobile-comic-desc")?.text()?.trim()
        manga.status = if (doc.selectFirst(".mobile-comic-meta")?.text()?.contains("完结") == true) {
            SManga.COMPLETED
        } else {
            SManga.ONGOING
        }
        return manga
    }

    // ================================ Chapters ================================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        return doc.select("div[data-mobile-chapter-list] a[href*='chapter.php']").map { el ->
            SChapter.create().apply {
                name = el.text().trim()
                url = el.attr("href")
                chapter_number = Regex("\\d+").find(name)?.value?.toFloat() ?: 0f
            }
        }
    }

    // ================================ Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        return doc.select("img.reading-image[src]").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun getFilterList(): FilterList = FilterList(listOf(GenreFilter()))
}

class GenreFilter :
    Filter.Select<String>(
        "题材",
        arrayOf(
            "全部",
            "热血",
            "玄幻",
            "都市",
            "恋爱",
            "古风",
            "穿越",
            "重生",
            "系统",
            "科幻",
            "奇幻",
            "灵异",
            "校园",
            "悬疑",
            "动作",
            "冒险",
            "搞笑",
            "耽美",
            "霸总",
            "剧情",
        ),
    )
