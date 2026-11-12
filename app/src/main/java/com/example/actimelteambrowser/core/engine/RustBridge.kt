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

    @JvmStatic external fun engineVersion(): String
    @JvmStatic external fun initAdblock(listText: String)
    @JvmStatic external fun shouldBlock(url: String, sourceUrl: String, resourceType: String): Boolean
    // kosmeticke filtry: CSS pro skryti reklam (podle adresy) a obecna pravidla (tridy/id oddelene novym radkem)
    @JvmStatic external fun cosmeticCss(url: String): String
    @JvmStatic external fun hiddenSelectors(classes: String, ids: String): String
}
