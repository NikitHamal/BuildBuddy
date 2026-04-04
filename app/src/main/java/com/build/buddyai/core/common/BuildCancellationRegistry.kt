package com.build.buddyai.core.common

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BuildCancellationRegistry @Inject constructor() {
    private val processes = ConcurrentHashMap<String, Process>()
    private val cancelledBuilds = ConcurrentHashMap.newKeySet<String>()

    fun register(buildId: String, process: Process) {
        processes[buildId]?.destroyForcibly()
        processes[buildId] = process
    }

    fun unregister(buildId: String) {
        processes.remove(buildId)
        cancelledBuilds.remove(buildId)
    }

    fun cancelBuild(buildId: String) {
        cancelledBuilds.add(buildId)
        processes.remove(buildId)?.destroyForcibly()
    }

    fun isCancelled(buildId: String): Boolean = cancelledBuilds.contains(buildId)
}
