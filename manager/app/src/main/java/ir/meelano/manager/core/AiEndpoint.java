package ir.meelano.manager.core;

import java.net.URI;
import java.net.URISyntaxException;

/** Validation for the user-configured AI proxy URL. Credentials are never accepted in a URL. */
public final class AiEndpoint {
    private AiEndpoint() { }

    /** Returns a normalized HTTPS endpoint or throws an actionable, non-secret error. */
    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("نشانی سرور AI را وارد کنید");
        }
        final URI uri;
        try {
            uri = new URI(value.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("ساختار نشانی سرور AI درست نیست");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getHost().trim().isEmpty()) {
            throw new IllegalArgumentException("سرور AI باید نشانی معتبر HTTPS داشته باشد");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("نشانی سرور نباید شامل رمز، query یا fragment باشد");
        }
        if (uri.getPort() < -1 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("شماره پورت نشانی سرور معتبر نیست");
        }
        String path = uri.getPath();
        if (path == null || path.isEmpty()) path = "/";
        try {
            return new URI("https", null, uri.getHost(), uri.getPort(), path, null, null)
                    .normalize().toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("نشانی سرور AI معتبر نیست");
        }
    }
}
