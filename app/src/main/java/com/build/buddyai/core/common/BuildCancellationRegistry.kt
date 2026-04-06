package com.build.buddyai.core.common

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildCancellationRegistry @Inject constructor() {
    private val cancelledBuilds = ConcurrentHashMap.newKeySet<String>()

    fun register(buildId: String, _process: Process) {
        // Reset stale cancellation flags when a build starts.
        cancelledBuilds.remove(buildId)
        // On-device compilers mostly run in-process; process registration is optional.
        // Keep API surface for future external process cancellation hooks.
    }

    fun unregister(buildId: String) {
        cancelledBuilds.remove(buildId)
    }

    fun cancelBuild(buildId: String) {
        cancelledBuilds.add(buildId)
    }

    fun isCancelled(buildId: String): Boolean = cancelledBuilds.contains(buildId)
}
