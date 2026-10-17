package kr.agv.cfgmanager.data

import kotlinx.coroutines.test.runTest
import kr.agv.cfgmanager.core.profiles.ProfileTemplate
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 가짜 암호화: 평문을 바이트 반전하고 IV 는 고정 값을 쓴다. 파일에 평문이 남지 않는지 검사하는 데 쓴다. */
class ReversingCipher : SecretCipher {
    override fun seal(plain: ByteArray): SealedBytes = SealedBytes(ByteArray(12) { 7 }, plain.map { (it.toInt() xor 0x5A).toByte() }.toByteArray())
    override fun open(sealed: SealedBytes): ByteArray = sealed.data.map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
}

class ProfileAndNetworkStoreTest {

    private lateinit var dir: File

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("store-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `첫 실행에는 본사 기본 프로필을 만들어 파일로 남긴다`() = runTest {
        val file = File(dir, "profiles.json")
        val doc = ProfileStore(file) { "2026-10-17T09:00:00Z" }.load()
        assertEquals(listOf("본사"), doc.profiles.map { it.name })
        assertEquals(ProfileTemplate.HEADQUARTERS_ID, doc.lastProfileId)
        assertTrue(file.exists())
        assertFalse(File(dir, "profiles.json.tmp").exists())
    }

    @Test
    fun `프로필 저장은 다시 읽어도 같고 마지막 사용 시각이 갱신된다`() = runTest {
        val file = File(dir, "profiles.json")
        val store = ProfileStore(file) { "2026-10-17T09:00:00Z" }
        val hq = store.load().profiles.single()
        store.upsert(hq.copy(name = "본사 1공장", notes = "메모"))
        store.markUsed(hq.id, "2026-10-17T11:00:00Z")

        val reread = ProfileStore(file).load()
        assertEquals("본사 1공장", reread.profiles.single().name)
        assertEquals("2026-10-17T11:00:00Z", reread.profiles.single().lastUsedAt)
        assertEquals("메모", reread.profiles.single().notes)
    }

    @Test
    fun `깨진 프로필 파일은 옮겨 보관하고 기본 프로필로 시작한다`() = runTest {
        val file = File(dir, "profiles.json")
        file.writeText("이건 JSON 이 아님")
        val store = ProfileStore(file) { "2026-10-17T09:00:00Z" }
        assertEquals(listOf("본사"), store.load().profiles.map { it.name })
        assertTrue(store.notice.value != null)
        assertTrue(dir.listFiles()!!.any { it.name.startsWith("profiles.corrupt-") })
    }

    @Test
    fun `SSID 라이브러리는 비밀번호를 암호화해 쓰고 평문을 파일에 남기지 않는다`() = runTest {
        val file = File(dir, "networks.json")
        val store = NetworkStore(file, ReversingCipher()) { "2026-10-17T10:00:00Z" }
        store.load()
        store.remember("AGV_WIFI", "supersecret99", "2026-10-17T10:00:00Z")

        val text = file.readText()
        assertFalse(text.contains("supersecret99"))
        assertTrue(text.contains("passwordEnc"))

        val reread = NetworkStore(file, ReversingCipher())
        reread.load()
        assertEquals("supersecret99", reread.password("AGV_WIFI"))
        assertEquals(1, reread.entries.value.single().useCount)
    }

    @Test
    fun `비밀번호 없이 기록하면 사용 횟수만 오르고, 빈 비밀번호는 오픈 AP 다`() = runTest {
        val file = File(dir, "networks.json")
        val store = NetworkStore(file, ReversingCipher())
        store.load()
        store.remember("GUEST_OPEN", "", "2026-10-17T10:00:00Z")
        store.remember("GUEST_OPEN", null, "2026-10-17T11:00:00Z")

        assertEquals("", store.password("GUEST_OPEN"))
        val entry = store.entries.value.single()
        assertTrue(entry.open)
        assertEquals(2, entry.useCount)
        assertEquals("2026-10-17T11:00:00Z", entry.lastUsedAt)
        assertNull(store.password("NOT_SAVED"))
    }

    @Test
    fun `삭제하면 목록과 파일에서 사라진다`() = runTest {
        val file = File(dir, "networks.json")
        val store = NetworkStore(file, ReversingCipher())
        store.load()
        store.remember("AGV_WIFI", "supersecret99")
        store.delete("AGV_WIFI")
        assertTrue(store.entries.value.isEmpty())
        assertTrue(NetworkStore(file, ReversingCipher()).also { it.load() }.entries.value.isEmpty())
    }
}
