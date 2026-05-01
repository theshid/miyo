package ani.saikou.data.repository

import ani.saikou.data.local.TokenStorage
import ani.saikou.data.remote.AnilistApi
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the JSON-parsing paths in [AnilistRepositoryImpl.getUserStats] —
 * the most defensive code in the file because AniList's stats payload is
 * deeply nested, every leaf is optional, and a single missing field used
 * to NPE the profile screen.
 *
 * Each test pre-builds a JsonObject that mimics the relevant slice of the
 * real AniList response and asserts the boundary the parser is supposed
 * to respect (null guards, defaults, drop-on-missing-key behavior).
 */
class AnilistRepositoryImplTest {
    private val api: AnilistApi = mockk()
    private val tokenStorage: TokenStorage = mockk(relaxed = true)
    private val repo = AnilistRepositoryImpl(api, tokenStorage)

    // -------- top-level guards --------

    @Test
    fun `getUserStats returns null when API returns null`() =
        runTest {
            coEvery { api.execute(any(), any()) } returns null

            assertNull(repo.getUserStats())
        }

    @Test
    fun `getUserStats returns null when data envelope has no Viewer`() =
        runTest {
            coEvery { api.execute(any(), any()) } returns
                buildJsonObject {
                    put("data", buildJsonObject { /* no Viewer key */ })
                }

            assertNull(repo.getUserStats())
        }

    @Test
    fun `getUserStats returns null when Viewer is JsonNull`() =
        runTest {
            // AniList returns Viewer:null for unauthenticated requests.
            coEvery { api.execute(any(), any()) } returns
                buildJsonObject {
                    put(
                        "data",
                        buildJsonObject { put("Viewer", JsonNull) },
                    )
                }

            assertNull(repo.getUserStats())
        }

    @Test
    fun `getUserStats returns null when Viewer has no statistics block`() =
        runTest {
            coEvery { api.execute(any(), any()) } returns
                response(
                    viewer =
                        buildJsonObject {
                            put("name", "alice")
                            // no statistics
                        },
                )

            assertNull(repo.getUserStats())
        }

    // -------- happy path --------

    @Test
    fun `getUserStats parses full anime and manga statistics`() =
        runTest {
            coEvery { api.execute(any(), any()) } returns
                response(
                    viewer =
                        buildJsonObject {
                            put("name", "alice")
                            put(
                                "avatar",
                                buildJsonObject { put("medium", "https://cdn/avatar.png") },
                            )
                            put(
                                "statistics",
                                buildJsonObject {
                                    put(
                                        "anime",
                                        buildJsonObject {
                                            put("count", 42)
                                            put("episodesWatched", 600)
                                            put("minutesWatched", 14_400)
                                            put("meanScore", 78.5)
                                            put(
                                                "genres",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            put("genre", "Action")
                                                            put("count", 12)
                                                            put("meanScore", 80f)
                                                            put("minutesWatched", 5_000)
                                                        },
                                                    )
                                                },
                                            )
                                            put(
                                                "statuses",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            put("status", "COMPLETED")
                                                            put("count", 35)
                                                        },
                                                    )
                                                },
                                            )
                                            put(
                                                "scores",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            put("score", 90)
                                                            put("count", 5)
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                    put(
                                        "manga",
                                        buildJsonObject {
                                            put("count", 18)
                                            put("chaptersRead", 1_200)
                                            put("volumesRead", 90)
                                            put("meanScore", 82f)
                                            put(
                                                "genres",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            put("genre", "Seinen")
                                                            put("count", 8)
                                                            put("meanScore", 85f)
                                                            put("chaptersRead", 700)
                                                        },
                                                    )
                                                },
                                            )
                                            put(
                                                "statuses",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            put("status", "READING")
                                                            put("count", 3)
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                )

            val out = repo.getUserStats()!!

            assertEquals("alice", out.userName)
            assertEquals("https://cdn/avatar.png", out.avatar)
            assertEquals(42, out.anime.count)
            assertEquals(600, out.anime.episodesWatched)
            assertEquals(14_400, out.anime.minutesWatched)
            assertEquals(78.5f, out.anime.meanScore)
            assertEquals(1, out.anime.genres.size)
            assertEquals("Action", out.anime.genres[0].genre)
            assertEquals(5_000, out.anime.genres[0].minutesWatched)
            assertEquals(1, out.anime.statuses.size)
            assertEquals("COMPLETED", out.anime.statuses[0].status)
            assertEquals(1, out.anime.scores.size)
            assertEquals(90, out.anime.scores[0].score)

            assertEquals(18, out.manga.count)
            assertEquals(1_200, out.manga.chaptersRead)
            assertEquals(90, out.manga.volumesRead)
            assertEquals("Seinen", out.manga.genres[0].genre)
            assertEquals(700, out.manga.genres[0].chaptersRead)
            assertEquals("READING", out.manga.statuses[0].status)
        }

    // -------- defaults / boundary --------

    @Test
    fun `falls back to User when name field is missing`() =
        runTest {
            coEvery { api.execute(any(), any()) } returns
                response(
                    viewer =
                        buildJsonObject {
                            put("statistics", buildJsonObject { /* empty */ })
                        },
                )

            val out = repo.getUserStats()

            assertEquals("User", out?.userName)
            assertNull(out?.avatar)
        }

    @Test
    fun `defaults AnimeStats when anime block is missing`() =
        runTest {
            coEvery { api.execute(any(), any()) } returns
                response(
                    viewer =
                        buildJsonObject {
                            put("name", "alice")
                            put(
                                "statistics",
                                buildJsonObject {
                                    put("manga", buildJsonObject { put("count", 5) })
                                    // no anime block
                                },
                            )
                        },
                )

            val out = repo.getUserStats()!!

            assertEquals(0, out.anime.count)
            assertEquals(0, out.anime.episodesWatched)
            assertTrue(out.anime.genres.isEmpty())
            assertTrue(out.anime.scores.isEmpty())
            assertEquals(5, out.manga.count)
        }

    @Test
    fun `defaults MangaStats when manga block is missing`() =
        runTest {
            coEvery { api.execute(any(), any()) } returns
                response(
                    viewer =
                        buildJsonObject {
                            put("name", "alice")
                            put(
                                "statistics",
                                buildJsonObject {
                                    put("anime", buildJsonObject { put("count", 5) })
                                    // no manga block
                                },
                            )
                        },
                )

            val out = repo.getUserStats()!!

            assertEquals(5, out.anime.count)
            assertEquals(0, out.manga.count)
            assertEquals(0, out.manga.chaptersRead)
            assertTrue(out.manga.genres.isEmpty())
        }

    // -------- drop-on-missing-key list parsers --------

    @Test
    fun `genre entries without genre name are dropped from the list`() =
        runTest {
            // First entry missing "genre" key — must be dropped silently rather
            // than fall through as a bogus null-named bucket.
            coEvery { api.execute(any(), any()) } returns
                response(
                    viewer =
                        buildJsonObject {
                            put("name", "alice")
                            put(
                                "statistics",
                                buildJsonObject {
                                    put(
                                        "anime",
                                        buildJsonObject {
                                            put(
                                                "genres",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            // Missing "genre" key — must be dropped.
                                                            put("count", 7)
                                                        },
                                                    )
                                                    add(
                                                        buildJsonObject {
                                                            put("genre", "Drama")
                                                            put("count", 3)
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                )

            val out = repo.getUserStats()!!

            assertEquals(1, out.anime.genres.size)
            assertEquals("Drama", out.anime.genres[0].genre)
        }

    @Test
    fun `zero-count score buckets are filtered out`() =
        runTest {
            // AniList returns the full 0..100 score grid; the renderer only
            // wants buckets with at least one rated entry.
            coEvery { api.execute(any(), any()) } returns
                response(
                    viewer =
                        buildJsonObject {
                            put("name", "alice")
                            put(
                                "statistics",
                                buildJsonObject {
                                    put(
                                        "anime",
                                        buildJsonObject {
                                            put(
                                                "scores",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            put("score", 50)
                                                            put("count", 0)
                                                        },
                                                    )
                                                    add(
                                                        buildJsonObject {
                                                            put("score", 90)
                                                            put("count", 4)
                                                        },
                                                    )
                                                    add(
                                                        buildJsonObject {
                                                            put("score", 100)
                                                            put("count", 0)
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                )

            val out = repo.getUserStats()!!

            assertEquals(1, out.anime.scores.size)
            assertEquals(90, out.anime.scores[0].score)
        }

    @Test
    fun `score entries without score value are dropped from the list`() =
        runTest {
            coEvery { api.execute(any(), any()) } returns
                response(
                    viewer =
                        buildJsonObject {
                            put("name", "alice")
                            put(
                                "statistics",
                                buildJsonObject {
                                    put(
                                        "anime",
                                        buildJsonObject {
                                            put(
                                                "scores",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            // Missing "score" key — must be dropped.
                                                            put("count", 4)
                                                        },
                                                    )
                                                    add(
                                                        buildJsonObject {
                                                            put("score", 80)
                                                            put("count", 2)
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                )

            val out = repo.getUserStats()!!

            assertEquals(1, out.anime.scores.size)
            assertEquals(80, out.anime.scores[0].score)
        }

    @Test
    fun `non-numeric int fields default to zero (intOrZero adapter)`() =
        runTest {
            // Defends against schema drift — if AniList ships a field as a
            // string we don't expect, the parser must keep going.
            coEvery { api.execute(any(), any()) } returns
                response(
                    viewer =
                        buildJsonObject {
                            put("name", "alice")
                            put(
                                "statistics",
                                buildJsonObject {
                                    put(
                                        "anime",
                                        buildJsonObject {
                                            put("count", "not-a-number")
                                            put("episodesWatched", 12)
                                        },
                                    )
                                },
                            )
                        },
                )

            val out = repo.getUserStats()!!

            assertEquals(0, out.anime.count)
            assertEquals(12, out.anime.episodesWatched)
        }

    // -------- helpers --------

    private fun response(viewer: JsonObject): JsonObject =
        buildJsonObject {
            put("data", buildJsonObject { put("Viewer", viewer) })
        }
}
