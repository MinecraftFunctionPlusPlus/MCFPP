package top.mcfpp.browser.jdk;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;

/** Use Bouncy Castle's portable digest primitives; no JVM security provider discovery. */
public final class MessageDigest {
    private final Digest digest;
    private MessageDigest(Digest digest) { this.digest = digest; }
    public static MessageDigest getInstance(String algorithm) {
        return new MessageDigest(switch (algorithm) {
            case "SHA-256" -> new SHA256Digest();
            case "MD5" -> new MD5Digest();
            default -> throw new IllegalArgumentException("Unsupported digest: " + algorithm);
        });
    }
    public void update(byte value) { digest.update(value); }
    public void update(byte[] bytes) { update(bytes, 0, bytes.length); }
    public void update(byte[] bytes, int offset, int count) { digest.update(bytes, offset, count); }
    public byte[] digest() {
        byte[] output = new byte[digest.getDigestSize()];
        digest.doFinal(output, 0); return output;
    }
    public byte[] digest(byte[] bytes) { update(bytes); return digest(); }
    public void reset() { digest.reset(); }
    public int getDigestLength() { return digest.getDigestSize(); }
}
