package hmgapi.app;

/**
 * Filled in by HMG-API's hooks (hmgapi.hook.HMG-API) when this app's process
 * starts: what the zygote loaded, as JSON. Null when the hooks aren't running.
 */
public final class HmgApiStatus {
    private HmgApiStatus() {}

    public static volatile String json;
}
