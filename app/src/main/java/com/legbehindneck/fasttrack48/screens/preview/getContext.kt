package com.legbehindneck.fasttrack48.screens.preview

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

@Composable
fun getContext(): Context {
	return if (LocalInspectionMode.current) {
		DummyContext()
	} else {
		LocalContext.current
	}
}
