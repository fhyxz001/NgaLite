package com.ngalite.app.data

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

/**
 * NGA 登录密码加密。
 *
 * 参考网页端 [js_jsencrypt]（JSEncrypt v2.3.1）：使用 NGA 内置 RSA 公钥、
 * PKCS#1 v1.5 填充加密明文，输出 Base64 字符串。Java 的
 * `RSA/ECB/PKCS1Padding` 与 JSEncrypt 默认行为一致。
 *
 * [js_jsencrypt]: 账号操作_files/js_jsencrypt.js
 */
object RsaCipher {

    /** NGA 登录页内置的 RSA 公钥（PEM 去头尾后的 Base64 模数）。 */
    private const val PUBLIC_KEY_BASE64 = (
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyKzZWDimCN1OCprqWUhF" +
        "UPhcwxDE62/BFVP6LtQHJu+65dm4YNmDvzitmcfaXW9YbhXnd4oP7j+6vpcgJQ+p" +
        "3ucySo1ZnqO0Bb2JKEtxpCmxe7IYXhFEkJqHpFYBTiAxQz2n2mX4JZy/ehBUSMjz" +
        "gzd0NdG6Ai1C42oCzYltUOjNWZUNHn1nqpElSWHnUWqkdN8+5ISP/ZMKiQdFANkE" +
        "qDGw3/34qyF+E/hVgrGF4/CcWNP/LJCdB6DYtx7VPlQZF0tP1s+q/++rC4rQ2wmV" +
        "l2V8zGh1j7ojZbt62hVjy6byK1E/2XYo97ZtL4KDW7F5jJMvSDRFR7901UR8hCdf" +
        "4wIDAQAB"
    )

    private val publicKey by lazy {
        val keySpec = X509EncodedKeySpec(Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT))
        KeyFactory.getInstance("RSA").generatePublic(keySpec)
    }

    private val cipher by lazy { Cipher.getInstance("RSA/ECB/PKCS1Padding") }

    /** 用 NGA 公钥加密 [plain]，返回 Base64 字符串（与网页端 _encrypt 一致）。 */
    fun encrypt(plain: String): String {
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val bytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
