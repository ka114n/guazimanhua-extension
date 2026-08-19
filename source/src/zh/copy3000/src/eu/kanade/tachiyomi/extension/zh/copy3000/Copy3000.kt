package eu.kanade.tachiyomi.extension.zh.copy3000

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
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Source
abstract class Copy3000 : HttpSource() {
    override val supportsLatest = true

    /*
     * Image ordering (chapter page) and chapter list (comicdetail endpoint) are
     * served as AES-128-CBC encrypted payloads. The key is a fixed site-wide
     * constant that ship inside the pages as `cct` / `ccz` variables; the iv is
     * embedded as the first 16 bytes of the payload and the ciphertext is the
     * remaining hex.
     */
    private val aesKey = "op0zzpvv.nmn.00p"

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    override fun headersBuilder() = super.headersBuilder()
        .add("User-Agent", userAgent)

    // ================================ Manga list ================================

    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * 50
        return GET("$baseUrl/comics?ordering=-datetime_updated&offset=$offset&limit=50", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseComicBoxPage(response.body.string())

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = parseComicBoxPage(response.body.string())

    private fun parseComicBoxPage(body: String): MangasPage {
        val doc = Jsoup.parse(body)
        val mangas = mutableListOf<SManga>()
        for (box in doc.select("div.exemptComic-box[list]")) {
            mangas += parseComicBriefs(box.attr("list")).map { brief ->
                SManga.create().apply {
                    url = "/comic/${brief.pathWord}"
                    title = brief.name
                    thumbnail_url = brief.cover
                }
            }
        }
        return MangasPage(mangas, mangas.size >= 50)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // The searchci API rejects limit > 30 and empty queries (HTTP 400).
        if (query.isBlank()) return popularMangaRequest(page)
        val offset = (page - 1) * 30
        val qType = filters.filterIsInstance<QueryTypeFilter>().firstOrNull()?.getValue() ?: "name"
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("kb")
            .addPathSegment("web")
            .addPathSegment("searchci")
            .addPathSegment("comics")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("platform", "2")
            .addQueryParameter("limit", "30")
            .addQueryParameter("q", query)
            .addQueryParameter("q_type", qType)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val body = response.body.string()
        if (!body.trimStart().startsWith("{")) return parseComicBoxPage(body)
        val json = JSONObject(body)
        val results = json.optJSONObject("results")
        val list = results?.optJSONArray("list") ?: JSONArray()
        val mangas = List(list.length()) { i ->
            val item = list.getJSONObject(i)
            SManga.create().apply {
                url = "/comic/${item.optString("path_word")}"
                title = item.optString("name")
                thumbnail_url = item.optString("cover")
            }
        }
        val offset = response.request.url.queryParameter("offset")?.toIntOrNull() ?: 0
        val total = results?.optInt("total") ?: mangas.size
        return MangasPage(mangas, offset + mangas.size < total)
    }

    private data class ComicBrief(val pathWord: String, val name: String, val cover: String)

    /*
     * `div.exemptComic-box` carries a `list` attribute containing python-repr
     * style dicts, e.g. [{'path_word': 'x', 'name': 'y', 'cover': 'z', ...}].
     */
    private fun parseComicBriefs(raw: String): List<ComicBrief> {
        val pattern =
            Regex("'path_word':\\s*'([^']*)'.*?'name':\\s*'([^']*)'.*?'cover':\\s*'([^']*)'")
        return pattern.findAll(raw).mapNotNull { m ->
            val cover = m.groupValues[3]
            if (cover.startsWith("http")) {
                ComicBrief(
                    pathWord = m.groupValues[1],
                    name = m.groupValues[2],
                    cover = cover,
                )
            } else {
                null
            }
        }.toList()
    }

    // ================================ Manga details ================================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        val manga = SManga.create()

        manga.url = response.request.url.encodedPath
        manga.title = doc.selectFirst(".comicParticulars-title h6")?.text()?.trim().orEmpty()
        manga.thumbnail_url = doc.selectFirst(".comicParticulars-left-img img")
            ?.let { img -> img.attr("data-src").ifBlank { img.attr("src") }.ifBlank { img.absUrl("src") } }

        val liMap = doc.select(".comicParticulars-title-right li").associate { li ->
            val label = li.selectFirst("span")?.text()?.removeSuffix("：")?.trim().orEmpty()
            label to li
        }

        val author = liMap["作者"]
            ?.select("a")
            ?.joinToString(",") { it.text().trim() }
        if (!author.isNullOrBlank()) manga.author = author

        manga.genre = liMap["題材"]
            ?.select("a")
            ?.joinToString(",") { it.text().trim() }
            ?.takeIf { it.isNotBlank() }

        val lastUpdate = liMap["最後更新"]?.text()?.substringAfter("最後更新：")?.trim()
        manga.description = buildString {
            doc.selectFirst(".comicParticulars-synopsis")?.text()?.trim()?.let(::append)
            if (lastUpdate != null && lastUpdate.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("更新時間：$lastUpdate")
            }
        }

        val statusText = liMap["狀態"]?.text()?.trim().orEmpty()
        manga.status = when {
            statusText.contains("連載") -> SManga.ONGOING
            statusText.contains("完結") || statusText.contains("已完結") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    // ================================ Chapters ================================

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.trimEnd('/').substringAfterLast('/')
        return GET("$baseUrl/comicdetail/$slug/chapters", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val json = JSONObject(response.body.string())
        val results = json.getString("results")
        val plain = decryptHex(results.substring(16), aesKey.toByteArray(Charsets.UTF_8), results.substring(0, 16))
        val data = JSONObject(plain).getJSONObject("groups").optJSONObject("default") ?: JSONObject()
        val chapters = data.optJSONArray("chapters") ?: JSONArray()
        val slug = data.optString("path_word").ifBlank {
            response.request.url.encodedPath.substringAfterLast('/')
        }
        return List(chapters.length()) { i ->
            val ch = chapters.getJSONObject(i)
            SChapter.create().apply {
                name = ch.optString("name")
                url = "/comic/$slug/chapter/${ch.optString("id")}"
                chapter_number = (i + 1).toFloat()
            }
        }
    }

    // ================================ Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val key = Regex("var\\s+cct\\s*=\\s*'([^']+)'").find(html)?.groupValues?.get(1)
            ?: throw Exception("cct not found")
        val contentKey = Regex("var\\s+contentKey\\s*=\\s*'([^']+)'").find(html)?.groupValues?.get(1)
            ?: throw Exception("contentKey not found")
        val plain = decryptHex(contentKey.substring(16), key.toByteArray(Charsets.UTF_8), contentKey.substring(0, 16))
        val arr = JSONArray(plain)
        return List(arr.length()) { i ->
            Page(i, imageUrl = arr.getJSONObject(i).optString("url"))
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    // ================================ Crypto ================================

    private fun decryptHex(hexData: String, key: ByteArray, ivHex: String): String {
        val ivText = ivHex.toByteArray(Charsets.UTF_8)
        require(hexData.length % 2 == 0) { "hex length must be even" }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivText))
        val out = cipher.doFinal(hexToBytes(hexData))
        return String(out, Charsets.UTF_8)
    }

    private fun hexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { i ->
        ((Character.digit(hex[i * 2], 16) shl 4) + Character.digit(hex[i * 2 + 1], 16)).toByte()
    }

    // ================================ Filters ================================

    override fun getFilterList(): FilterList = FilterList(
        listOf(
            Filter.Header("搜索類型"),
            QueryTypeFilter(),
        ),
    )

    private class QueryTypeFilter :
        Filter.Select<String>(
            "搜索類型",
            arrayOf("標題", "作者", "全部"),
            // 0->name, 1->author, 2->"" (all)
        ) {
        fun getValue(): String = when (state) {
            1 -> "author"
            2 -> ""
            else -> "name"
        }
    }
}
