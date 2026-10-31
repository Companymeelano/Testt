package ir.meelano.android;

import org.json.JSONObject;

import java.net.URL;
import java.util.Locale;
import java.util.regex.Pattern;

/** Strict parser and policy for the signed all-in-one app's update feed. */
final class MeelanoUpdateManifest {
    static final String PACKAGE_NAME = "ir.meelano.visitor";
    static final String META_URL = "https://raw.githubusercontent.com/Companymeelano/Testt/arena/29e01a09-testt/dist/latest-unified.json";
    static final String RELEASE_SIGNER_SHA256 = "1a2ed0dd70759d6e10c77a4641eeeb8f15972b76d99af351470216955b46d616";
    static final long MAX_APK_BYTES = 200L * 1024L * 1024L;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern VERSION_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}");

    final int versionCode;
    final String versionName;
    final String packageName;
    final String url;
    final String sha256;
    final String signerSha256;
    final long size;

    private MeelanoUpdateManifest(int code, String name, String pkg, String url,
                                  String sha, String signer, long size) {
        this.versionCode = code;
        this.versionName = name;
        this.packageName = pkg;
        this.url = url;
        this.sha256 = sha.toLowerCase(Locale.ROOT);
        this.signerSha256 = signer.toLowerCase(Locale.ROOT);
        this.size = size;
    }

    static MeelanoUpdateManifest parse(String json, long installedVersionCode) throws Exception {
        if (json == null || json.length() > 16_384) throw new IllegalArgumentException("manifest size");
        JSONObject obj = new JSONObject(json);
        long code = obj.getLong("versionCode");
        String name = obj.getString("versionName").trim();
        String pkg = obj.getString("packageName").trim();
        String url = obj.getString("url").trim();
        String sha = obj.getString("sha256").trim();
        String signer = obj.getString("signerSha256").trim();
        long size = obj.getLong("size");

        if (code <= 0 || code > Integer.MAX_VALUE || code <= installedVersionCode)
            throw new IllegalArgumentException("version is not newer");
        if (!VERSION_NAME.matcher(name).matches()) throw new IllegalArgumentException("invalid version name");
        if (!PACKAGE_NAME.equals(pkg)) throw new IllegalArgumentException("unexpected package");
        if (!SHA256.matcher(sha).matches()) throw new IllegalArgumentException("invalid file digest");
        if (!SHA256.matcher(signer).matches()
                || !RELEASE_SIGNER_SHA256.equals(signer.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("unexpected release signer");
        if (size <= 0 || size > MAX_APK_BYTES) throw new IllegalArgumentException("invalid APK size");

        URL download = new URL(url);
        if (!"https".equalsIgnoreCase(download.getProtocol())
                || download.getUserInfo() != null
                || (download.getPort() != -1 && download.getPort() != 443)
                || !"github.com".equalsIgnoreCase(download.getHost())
                || !download.getPath().startsWith("/Companymeelano/Testt/releases/download/")
                || !download.getPath().endsWith(".apk"))
            throw new IllegalArgumentException("untrusted download URL");

        return new MeelanoUpdateManifest((int) code, name, pkg, url, sha, signer, size);
    }
}
