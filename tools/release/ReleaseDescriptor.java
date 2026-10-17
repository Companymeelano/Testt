import ir.meelano.licensing.License;

/**
 * Builds the signed update descriptor (version.json) for a manager release.
 *
 * Usage (CI):
 *   java ReleaseDescriptor <versionCode> <versionName> <apkFile> <sizeBytes> <sha256> <url> <message>
 *
 * Prints the JSON to stdout. Exits non-zero if the MAC it produced does not
 * verify with the same code path the phones use (License.updateVerify), so a
 * broken secret or format can never reach a published release.
 */
public final class ReleaseDescriptor {
    private ReleaseDescriptor() { }

    public static void main(String[] args) {
        if (args.length != 7) {
            System.err.println("usage: ReleaseDescriptor <versionCode> <versionName> <apkFile> <sizeBytes> <sha256> <url> <message>");
            System.exit(2);
        }
        int code = Integer.parseInt(args[0].trim());
        String name = args[1].trim();
        String apk = args[2].trim();
        long size = Long.parseLong(args[3].trim());
        String sha = args[4].trim().toLowerCase(java.util.Locale.US);
        String url = args[5].trim();
        String msg = args[6];

        if (code <= 0 || name.isEmpty() || apk.isEmpty() || size <= 0) fail("bad fields");
        if (!sha.matches("[0-9a-f]{64}")) fail("sha256 must be 64 hex chars");
        if (!url.startsWith("https://github.com/")) fail("url must be a GitHub https URL");

        String mac = License.updateMac(code, sha);
        if (!License.updateVerify(code, sha, mac)) fail("MAC self-check failed");

        StringBuilder j = new StringBuilder();
        j.append("{\n  \"manager\": {\n");
        j.append("    \"code\": ").append(code).append(",\n");
        j.append("    \"name\": ").append(quote(name)).append(",\n");
        j.append("    \"apk\": ").append(quote(apk)).append(",\n");
        j.append("    \"size\": ").append(size).append(",\n");
        j.append("    \"sha256\": ").append(quote(sha)).append(",\n");
        j.append("    \"url\": ").append(quote(url)).append(",\n");
        j.append("    \"msg\": ").append(quote(msg)).append(",\n");
        j.append("    \"mac\": ").append(quote(mac)).append("\n");
        j.append("  }\n}\n");
        System.out.print(j);
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    private static void fail(String why) {
        System.err.println("ReleaseDescriptor: " + why);
        System.exit(1);
    }
}
