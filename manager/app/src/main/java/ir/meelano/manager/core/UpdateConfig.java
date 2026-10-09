package ir.meelano.manager.core;

/**
 * The ONE place a new release is published: a GitHub Release on the app
 * repository carrying the signed APKs plus a signed {@code version.json}
 * descriptor. Every licensed phone (bought or trial) checks this address
 * on entry; when a newer descriptor is there, the user is offered a
 * one-tap secure download + install.
 *
 * If the repository is ever renamed, update OWNER / REPO below (and the
 * release workflow) — until then the app just sees «no update», nothing breaks.
 */
public final class UpdateConfig {
    private UpdateConfig() { }

    public static final String OWNER = "Companymeelano";
    public static final String REPO = "Testt";

    /** Always the descriptor of the newest published (non-draft, non-prerelease) release. */
    public static final String META_URL = "https://github.com/" + OWNER + "/" + REPO
            + "/releases/latest/download/version.json";

    /** Silent automatic check at most once a day (manual checks are unlimited). */
    public static final long AUTO_INTERVAL_MS = 24L * 60 * 60 * 1000;

    public static final int CONNECT_TIMEOUT_MS = 12000;
    public static final int READ_TIMEOUT_MS = 25000;

    /** Sanity cap: any APK bigger than this is refused, no questions asked. */
    public static final long MAX_APK_BYTES = 200L * 1024 * 1024;
}
