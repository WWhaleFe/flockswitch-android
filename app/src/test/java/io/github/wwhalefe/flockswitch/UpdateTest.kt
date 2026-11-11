package io.github.wwhalefe.flockswitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdateTest {
    private val apkDigest = "3f".repeat(32)

    @Test
    fun sumsAsSha256sumWritesThem() {
        // What `sha256sum flockswitch-android-*.apk > SHA256SUMS.txt` produces.
        val sums = parseSha256Sums("$apkDigest  flockswitch-android-0.7.0.apk\n")
        assertEquals(mapOf("flockswitch-android-0.7.0.apk" to apkDigest), sums)
    }

    @Test
    fun sumsInBinaryModeUpperCaseAndWithPaths() {
        val upper = "AB".repeat(32)
        val sums = parseSha256Sums(
            """
            ${upper} *flockswitch-android-0.7.0.apk
            ${apkDigest}  out/other.apk
            """.trimIndent() + "\r\n",
        )
        assertEquals(upper.lowercase(), sums["flockswitch-android-0.7.0.apk"])
        assertEquals(apkDigest, sums["other.apk"])
    }

    @Test
    fun sumsSkipWhatIsNotAChecksumLine() {
        val sums = parseSha256Sums(
            """
            # comment
            abc  short.apk
            ${"g".repeat(64)}  nothex.apk
            ${apkDigest}
            ${apkDigest}  good.apk

            """.trimIndent(),
        )
        assertEquals(mapOf("good.apk" to apkDigest), sums)
        assertTrue(parseSha256Sums("").isEmpty())
    }

    @Test
    fun aFileMatchesOnlyItsOwnChecksum() {
        val f = File.createTempFile("flockswitch", ".apk")
        try {
            f.writeText("abc")
            // SHA-256 of "abc".
            val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            assertEquals(abc, sha256(f))
            // As SelfUpdate checks a download: the digest listed for its name, or none.
            fun matches(sums: String) = parseSha256Sums(sums)["flockswitch-android-0.7.0.apk"] == sha256(f)
            assertTrue(matches("$abc  flockswitch-android-0.7.0.apk\n"))
            assertFalse(matches("$apkDigest  flockswitch-android-0.7.0.apk\n"))
            // Not listed at all: never a match.
            assertFalse(matches("$abc  flockswitch-android-0.6.0.apk\n"))
        } finally {
            f.delete()
        }
    }

    @Test
    fun releaseAssetsAreFoundByName() {
        val body = """
            {"tag_name": "v0.7.0", "assets": [
              {"name": "SHA256SUMS.txt", "browser_download_url": "https://github.com/x/SHA256SUMS.txt"},
              {"name": "flockswitch-android-0.7.0.apk", "browser_download_url": "https://github.com/x/flockswitch-android-0.7.0.apk"}
            ]}
        """.trimIndent()
        val r = parseRelease(body)
        assertEquals("0.7.0", r.version)
        assertEquals("https://github.com/x/flockswitch-android-0.7.0.apk", r.apkUrl)
        assertEquals("https://github.com/x/SHA256SUMS.txt", r.sumsUrl)
    }

    @Test
    fun aReleaseWithoutItsFilesHasNoUrls() {
        val r = parseRelease("""{"tag_name": "v0.7.0", "assets": [{"name": "flockswitch-android-0.6.0.apk", "browser_download_url": "https://x"}]}""")
        assertNull(r.apkUrl)
        assertNull(r.sumsUrl)
        assertNull(parseRelease("""{"tag_name": "0.7.0"}""").apkUrl)
    }

    @Test
    fun apkNameFollowsTheReleaseWorkflow() = assertEquals("flockswitch-android-0.7.0.apk", apkName("0.7.0"))
}
