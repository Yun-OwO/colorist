package com.colorist.core

import net.neoforged.fml.LogicalSide
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Lightweight logger facade for Colorist. Mirrors the `console.log` style used
 * throughout the original KubeJS `lib.js`.
 */
object ColorLog {
    val logger: Logger = LoggerFactory.getLogger("Colorist")

    fun info(msg: String) = logger.info(msg)
    fun warn(msg: String) = logger.warn(msg)
    fun error(msg: String, t: Throwable? = null) {
        if (t != null) logger.error(msg, t) else logger.error(msg)
    }

    /** Logical side this code is running on; set by the relevant entrypoint. */
    var side: LogicalSide = LogicalSide.SERVER
        internal set
}
