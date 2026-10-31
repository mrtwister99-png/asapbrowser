package com.example.actimelteambrowser.core.engine

object RustBridge {
    private var loadError: String? = null
    private var loaded = false

    init {
        try {
            System.loadLibrary("myytbrowser_engine")
            loaded = true
        } catch (e: Exception) {
            loadError = e.message ?: e.toString()
            e.printStackTrace()
        } catch (e: UnsatisfiedLinkError) {
            loadError = e.message ?: e.toString()
            e.printStackTrace()
        }
    }

    fun isAvailable(): Boolean = loaded && loadError == null
    fun getLoadError(): String = loadError ?: "unknown"

    @JvmStatic external fun engineVersion(): String
    @JvmStatic external fun initAdblock(listText: String)
    @JvmStatic external fun shouldBlock(url: String, sourceUrl: String, resourceType: String): Boolean
    @JvmStatic external fun cleanUrl(url: String): String
    @JvmStatic external fun youtubeAdblockCss(): String
    @JvmStatic external fun youtubeAdblockScript(): String
    @JvmStatic external fun youtubeAdblockFull(): String
    @JvmStatic external fun initJsEngineForTab(tabId: String, enabled: Boolean): String
    @JvmStatic external fun evalJs(tabId: String, code: String, url: String): String
    @JvmStatic external fun destroyJsEngineForTab(tabId: String)
    @JvmStatic external fun antiFingerprintScript(profileId: Long, dayEpoch: Long): String
    // kosmeticke filtry: CSS pro skryti reklam (podle adresy) a obecna pravidla (tridy/id oddelene novym radkem)
    @JvmStatic external fun cosmeticCss(url: String): String
    @JvmStatic external fun hiddenSelectors(classes: String, ids: String): String
}
