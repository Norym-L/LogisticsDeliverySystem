package norym.logisticsdeliverysystem.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 口令摘要：SHA-256(salt + 明文)，十六进制小写。
 * <p>
 * 与 sql/seed.sql 里生成种子账号的方式一致（salt = MD5(username) 前 16 位）。
 * 生产环境应换成 BCrypt/Argon2 这类带工作因子的算法，这里为了不引依赖用了标准库。
 */
public final class PasswordHasher
{
    private PasswordHasher()
    {
    }

    public static String hash(String salt, String rawPassword)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((salt + rawPassword).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    public static boolean matches(String expectedHash, String salt, String rawPassword)
    {
        if (expectedHash == null || salt == null || rawPassword == null)
        {
            return false;
        }
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8),
                hash(salt, rawPassword).getBytes(StandardCharsets.UTF_8));
    }
}
