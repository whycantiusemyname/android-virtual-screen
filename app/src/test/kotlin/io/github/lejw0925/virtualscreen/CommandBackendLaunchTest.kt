package io.github.lejw0925.virtualscreen

import android.app.Application
import android.content.Context
import io.github.lejw0925.virtualscreen.backend.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private class RecordingCommandBackend(context: Context) : CommandBackend(context) {
    override val kind = BackendKind.ROOT
    var lastRequest: JSONObject? = null
    var nextResponse = JSONObject().put("ok", true)

    override suspend fun transact(request: JSONObject): JSONObject {
        lastRequest = JSONObject(request.toString())
        return JSONObject(nextResponse.toString())
    }

    override suspend fun connect(config: VirtualScreenConfig) = JSONObject()
        .put("ok", true).put("handleId", "test-handle").put("displayId", 7)

    override suspend fun disconnect() = Unit
    override suspend fun probe() = BackendCapabilities(displayCreation = CapabilityStatus.AVAILABLE)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CommandBackendLaunchTest {
    @Test fun launchCommandCarriesOnlyTheRequestedOptInAndPreservesBackendFailure() = runBlocking {
        val backend = RecordingCommandBackend(RuntimeEnvironment.getApplication())
        val handle = backend.create(VirtualScreenConfig()).getOrThrow()

        val defaultResult = backend.launch(handle, "fixture/.Main")
        assertTrue(defaultResult.ok)
        assertFalse(backend.lastRequest!!.getBoolean("allowUnresizable"))
        assertEquals("fixture/.Main", backend.lastRequest!!.getString("component"))

        backend.nextResponse = JSONObject().put("ok", false).put("code", "DISPLAY_LAUNCH_REJECTED")
        val optedInResult = backend.launch(handle, "fixture/.Main", uri = null, restartApproved = false, allowUnresizable = true)
        assertTrue(backend.lastRequest!!.getBoolean("allowUnresizable"))
        assertFalse(optedInResult.ok)
        assertEquals("DISPLAY_LAUNCH_REJECTED", optedInResult.code)
    }
}
