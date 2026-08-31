package com.legbehindneck.fasttrack48.data.activefast

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Instant

/**
 * A fake implementation of ActiveFastDataSource for testing purposes.
 *
 * Backed by a StateFlow rather than two plain fields so that the fake publishes writes the
 * way the preferences datasource does; a fake that only stored them would let a regression
 * in the observable path pass its tests.
 */
class FakeActiveFastDataSource : ActiveFastDataSource {
	private val state = MutableStateFlow(ActiveFastWindow())

	override fun getFastStart(): Instant? = state.value.start

	override fun getFastEnd(): Instant? = state.value.end

	override fun observe(): Flow<ActiveFastWindow> = state.asStateFlow()

	override fun setFastStart(time: Instant) {
		state.value = state.value.copy(start = time)
	}

	override fun setFastEnd(time: Instant) {
		state.value = state.value.copy(end = time)
	}

	override fun clearFastStart() {
		state.value = state.value.copy(start = null)
	}

	override fun clearFastEnd() {
		state.value = state.value.copy(end = null)
	}

	/**
	 * Clears all data - useful for test setup/teardown
	 */
	fun clear() {
		state.value = ActiveFastWindow()
	}
}
