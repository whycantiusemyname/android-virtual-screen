package io.github.lejw0925.virtualscreen.backend

import android.app.Application
import android.view.InputDevice
import android.view.MotionEvent
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RootMotionStreamTest {
    private data class Dispatched(val action: Int, val pointerIds: List<Int>)

    @Test fun rejectedUpCancelsTheLastDispatchedTouch() = failedDispatch(MotionEvent.ACTION_UP, false)

    @Test fun throwingUpCancelsTheLastDispatchedTouch() = failedDispatch(MotionEvent.ACTION_UP, true)

    @Test fun rejectedCancelStillAttemptsToReleaseTheTouch() = failedDispatch(MotionEvent.ACTION_CANCEL, false)

    @Test fun rejectedPointerUpCancelsBothPreviouslyDispatchedPointers() = failedDispatch(
        MotionEvent.ACTION_POINTER_UP, false,
        listOf(
            event(MotionEvent.ACTION_DOWN, 0),
            event(MotionEvent.ACTION_POINTER_DOWN, 0, 1),
            event(MotionEvent.ACTION_POINTER_UP, 0, 1),
        ),
        listOf(0, 1),
    )

    @Test fun cleanupExcludesAPointerWhoseUpWasAlreadyDispatched() = failedDispatch(
        MotionEvent.ACTION_MOVE, false,
        listOf(
            event(MotionEvent.ACTION_DOWN, 0),
            event(MotionEvent.ACTION_POINTER_DOWN, 0, 1),
            event(MotionEvent.ACTION_POINTER_UP, 0, 1),
            event(MotionEvent.ACTION_MOVE, 0),
        ),
    )

    @Test fun successfulUpNeedsNoCancellation() {
        val dispatched = Collections.synchronizedList(mutableListOf<Int>())
        val injector = RootInputInjector(7, dispatch = { input, _ ->
            dispatched += (input as MotionEvent).actionMasked
            true
        })
        val stream = RootMotionStream(injector, { true }, {}, { _, code -> fail(code) })
        try {
            enqueue(stream, 1, listOf(event(MotionEvent.ACTION_DOWN, 0), event(MotionEvent.ACTION_UP, 0)))
            stream.awaitSettled(1)
            assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), dispatched.toList())
        } finally {
            stream.close()
            injector.close()
        }
    }

    private fun failedDispatch(
        failedAction: Int,
        throws: Boolean,
        events: List<MotionEvent> = listOf(event(MotionEvent.ACTION_DOWN, 0), event(failedAction, 0)),
        expectedCancelPointers: List<Int> = listOf(0),
    ) {
        val dispatched = Collections.synchronizedList(mutableListOf<Dispatched>())
        val failed = CountDownLatch(1)
        val failureCode = AtomicReference<String>()
        val failOnce = AtomicBoolean(true)
        val injector = RootInputInjector(7, dispatch = { input, _ ->
            val motion = input as MotionEvent
            dispatched += Dispatched(motion.actionMasked, (0 until motion.pointerCount).map(motion::getPointerId))
            if (motion.actionMasked == failedAction && failOnce.getAndSet(false)) {
                if (throws) error("Synthetic input dispatch failure")
                false
            } else true
        })
        val stream = RootMotionStream(injector, { true }, {}, { _, code ->
            failureCode.set(code)
            failed.countDown()
        })
        try {
            enqueue(stream, 1, events)
            assertTrue("Dispatch failure must be reported", failed.await(5, TimeUnit.SECONDS))
            stream.awaitSettled(1)
            assertEquals("INPUT_DISPATCH_UNCONFIRMED", failureCode.get())
            assertEquals(Dispatched(MotionEvent.ACTION_CANCEL, expectedCancelPointers), dispatched.last())
            assertEquals(events.size + 1, dispatched.size)

            // Cleanup must also reset the queue for the next gesture.
            enqueue(stream, 2, listOf(event(MotionEvent.ACTION_DOWN, 0), event(MotionEvent.ACTION_UP, 0)))
            stream.awaitSettled(2)
            assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), dispatched.takeLast(2).map { it.action })
        } finally {
            stream.close()
            injector.close()
        }
    }

    private fun enqueue(stream: RootMotionStream, gesture: Long, events: List<MotionEvent>) {
        try {
            events.forEach { stream.enqueue(LiveGeometry(720, 1600, 0), gesture, it) }
        } finally { events.forEach(MotionEvent::recycle) }
    }

    private fun event(action: Int, vararg ids: Int): MotionEvent {
        val pointerAction = if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP)
            action or ((ids.size - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT) else action
        val properties = ids.map { id -> MotionEvent.PointerProperties().apply {
            this.id = id
            toolType = MotionEvent.TOOL_TYPE_FINGER
        } }.toTypedArray()
        val coords = ids.map { id -> MotionEvent.PointerCoords().apply {
            x = 30f + id * 20f
            y = 40f
            pressure = 1f
            size = 1f
        } }.toTypedArray()
        return MotionEvent.obtain(10, 20, pointerAction, ids.size, properties, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    }
}
