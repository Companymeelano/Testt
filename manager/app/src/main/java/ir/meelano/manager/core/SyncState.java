package ir.meelano.manager.core;

/** Dashboard synchronization state; cached data availability and network state stay distinct. */
public enum SyncState {
    FRESH,
    REFRESHING,
    STALE,
    OFFLINE,
    ERROR;

    /** Resolve a failed refresh without misrepresenting cached data as fresh or a server error as offline. */
    public static SyncState afterFailure(boolean hasCachedData, boolean networkAvailable) {
        if (!networkAvailable) return OFFLINE;
        return hasCachedData ? STALE : ERROR;
    }
}
