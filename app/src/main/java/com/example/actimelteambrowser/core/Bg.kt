package com.example.actimelteambrowser.core

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Spolecna vlakna pro drobnou praci na pozadi, ktera se deje pri kazdem nacteni stranky nebo psani
 * (kosmeticke filtry, zapis historie, naseptavac, ikony a nahledy webu). Drive vznikalo pro kazdou akci nove vlakno.
 * Dlouhe veci (stahovani, export, screenshot) maji dal vlastni vlakno, aby tyhle drobnosti nebrzdily.
 */
private val count = AtomicInteger()
private val pool = Executors.newFixedThreadPool(3, ThreadFactory { r ->
    Thread(r, "bg-" + count.incrementAndGet()).apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
})

internal fun bg(block: () -> Unit) {
    pool.execute { runCatching(block).onFailure { ErrorLog.note(it, "bg") } }
}
