package kr.agv.cfgmanager.data

/** 암호문과 IV. 파일에는 base64 로 나눠 쓴다 (부록 B-2 `passwordEnc`). */
class SealedBytes(val iv: ByteArray, val data: ByteArray)

/**
 * 비밀번호 암호화 경계. 실제 구현은 [KeystoreCipher] (Android Keystore AES-GCM),
 * JVM 테스트는 가짜 구현을 주입한다.
 */
interface SecretCipher {
    fun seal(plain: ByteArray): SealedBytes
    fun open(sealed: SealedBytes): ByteArray
}
