package com.legbehindneck.fasttrack48.screens.fasting

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.legbehindneck.fasttrack48.ui.theme.FastTrackTheme
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@Preview(showBackground = true, widthDp = 360)
@Composable
fun FastStartBlockPreview() {
	FastTrackTheme {
		Surface {
			FastStartBlock(
				startTime = Clock.System.now() - 3.hours,
				onEditStart = {},
				modifier = Modifier
					.fillMaxWidth()
					.padding(13.dp),
			)
		}
	}
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun FastStartBlockOlderPreview() {
	FastTrackTheme {
		Surface {
			FastStartBlock(
				startTime = Clock.System.now() - 30.hours,
				onEditStart = {},
				modifier = Modifier
					.fillMaxWidth()
					.padding(13.dp),
			)
		}
	}
}
