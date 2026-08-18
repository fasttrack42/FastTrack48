package com.legbehindneck.fasttrack48.screens.main

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.legbehindneck.fasttrack48.R
import kotlinx.coroutines.flow.collectLatest

private const val GITHUB_URL = "https://github.com/fasttrack42/FastTrack48"
private const val WEBSITE_URL = "https://fasttrack42.github.io/FastTrack48"
private const val TELEGRAM_URL = "https://t.me/FastTrack48"

/** The project this app is a fork of, and its author. See AcknowledgementsSection. */
private const val UPSTREAM_REPO_URL = "https://github.com/Darkrock-Studios/FastTrack"
private const val UPSTREAM_AUTHOR_URL = "https://github.com/Wavesonics"

/**
 * The About dialog in Compose, replacing the retired MaterialAbout library
 * (the last dependency that needed Jetifier). Cover photo with the studio
 * avatar overlapping its lower edge, link chips, then the app block with
 * rate and share actions. Spacing follows the app's Fibonacci scale.
 *
 * Provenance sits with identity: the Acknowledgements block follows the
 * name/version row, so "what this app is" and "where it came from" read as
 * one thought rather than as a legal footnote.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutDialog(
	versionName: String,
	onOpenUrl: (String) -> Unit,
	onRateApp: () -> Unit,
	onShareApp: () -> Unit,
	onDismiss: () -> Unit,
) {
	Dialog(onDismissRequest = onDismiss) {
		val cardColor = CardDefaults.cardColors().containerColor
		val scrollState = rememberScrollState()

		Card(modifier = Modifier.widthIn(max = 420.dp)) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.verticalScroll(scrollState),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				// Cover photo with the avatar overlapping its lower edge
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(188.dp)
				) {

					IconButton(
						onClick = onDismiss,
						modifier = Modifier
							.align(Alignment.TopEnd)
							.padding(8.dp),
						colors = IconButtonDefaults.iconButtonColors(
							containerColor = Color.Black.copy(alpha = 0.35f),
							contentColor = Color.White,
						),
					) {
						Icon(
							imageVector = Icons.Default.Close,
							contentDescription = stringResource(id = R.string.close_button_content_description),
						)
					}

					Image(
						painter = painterResource(id = R.drawable.ic_launcher_foreground),
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.size(89.dp)
							.clip(CircleShape)
							.border(3.dp, cardColor, CircleShape)
					)
				}

				Spacer(modifier = Modifier.height(8.dp))

				Text(
					text = stringResource(id = R.string.about_name),
					style = MaterialTheme.typography.titleLarge,
					fontWeight = FontWeight.SemiBold,
				)
				Text(
					text = stringResource(id = R.string.about_subtitle),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
				)

				Spacer(modifier = Modifier.height(13.dp))

				Text(
					text = stringResource(id = R.string.about_brief),
					style = MaterialTheme.typography.bodyMedium,
					textAlign = TextAlign.Center,
					modifier = Modifier.padding(horizontal = 34.dp),
				)

				Spacer(modifier = Modifier.height(13.dp))

				// Link chips wrap on narrow screens instead of overflowing
				FlowRow(
					modifier = Modifier.padding(horizontal = 21.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
				) {
					AssistChip(
						onClick = { onOpenUrl(GITHUB_URL) },
						label = { Text(stringResource(id = R.string.about_github)) },
						leadingIcon = {
							Icon(
								imageVector = Icons.Default.Code,
								contentDescription = null,
								modifier = Modifier.size(AssistChipDefaults.IconSize),
							)
						},
					)
					AssistChip(
						onClick = { onOpenUrl(WEBSITE_URL) },
						label = { Text(stringResource(id = R.string.about_website)) },
						leadingIcon = {
							Icon(
								imageVector = Icons.Default.Language,
								contentDescription = null,
								modifier = Modifier.size(AssistChipDefaults.IconSize),
							)
						},
					)
					AssistChip(
						onClick = { onOpenUrl(TELEGRAM_URL) },
						label = { Text(stringResource(id = R.string.about_telegram)) },
						leadingIcon = {
							Icon(
								painter = painterResource(id = R.drawable.ic_telegram),
								contentDescription = null,
								modifier = Modifier.size(AssistChipDefaults.IconSize),
							)
						},
					)
				}

				Spacer(modifier = Modifier.height(13.dp))

				HorizontalDivider(modifier = Modifier.padding(horizontal = 21.dp))

				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 21.dp, vertical = 13.dp),
					horizontalArrangement = Arrangement.Center,
					verticalAlignment = Alignment.CenterVertically,
				) {
					Column {
						Text(
							text = stringResource(id = R.string.app_name),
							style = MaterialTheme.typography.titleMedium,
						)
						Text(
							text = "v$versionName",
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
						)
					}
				}

				HorizontalDivider(modifier = Modifier.padding(horizontal = 21.dp))

				AcknowledgementsSection(
					onOpenUrl = onOpenUrl,
					scrollState = scrollState,
					modifier = Modifier.padding(horizontal = 21.dp, vertical = 13.dp),
				)

				HorizontalDivider(modifier = Modifier.padding(horizontal = 21.dp))

				Spacer(modifier = Modifier.height(13.dp))

				Column(
					modifier = Modifier
						.fillMaxWidth()
						.padding(start = 21.dp, end = 21.dp, bottom = 21.dp)
				) {
					Button(
						onClick = onRateApp,
						modifier = Modifier
							.fillMaxWidth()
							.heightIn(min = 48.dp),
					) {
						Icon(
							imageVector = Icons.Default.Star,
							contentDescription = null,
							modifier = Modifier.size(18.dp),
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(stringResource(id = R.string.about_rate))
					}

					Spacer(modifier = Modifier.height(8.dp))

					OutlinedButton(
						onClick = onShareApp,
						modifier = Modifier
							.fillMaxWidth()
							.heightIn(min = 48.dp),
					) {
						Icon(
							imageVector = Icons.Default.Share,
							contentDescription = null,
							modifier = Modifier.size(18.dp),
						)
						Spacer(modifier = Modifier.width(8.dp))
						Text(stringResource(id = R.string.action_share))
					}
				}
			}
		}
	}
}

/**
 * MIT attribution for the upstream project this app was forked from.
 *
 * The split is deliberate and is as much a legal decision as a visual one:
 * everything a person needs in order to understand the app's provenance -
 * who wrote the original, what it is called, under which licence, and the
 * explicit statement that the original author does not endorse this fork -
 * is always on screen and never behind a tap. Only the verbatim licence
 * text, which MIT requires to be *included* rather than displayed unprompted,
 * collapses; it is one tap away, selectable so it can be copied, and the
 * dialog scrolls itself to follow the expansion so the reveal never happens
 * below the fold.
 *
 * Type here follows the same ratio as the rest of the app: 13sp on 21sp,
 * consecutive Fibonacci numbers, so the leading is phi times the size.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcknowledgementsSection(
	onOpenUrl: (String) -> Unit,
	scrollState: ScrollState,
	modifier: Modifier = Modifier,
) {
	var licenseExpanded by remember { mutableStateOf(false) }
	val chevronRotation by animateFloatAsState(
		targetValue = if (licenseExpanded) 180f else 0f,
		label = "acknowledgements-chevron",
	)

	// Follow the unfurling content so the licence never opens below the fold.
	// collectLatest cancels the in-flight scroll on each growth step, which
	// keeps the motion continuous instead of stuttering per frame.
	LaunchedEffect(licenseExpanded) {
		if (!licenseExpanded) return@LaunchedEffect
		snapshotFlow { scrollState.maxValue }
			.collectLatest { scrollState.animateScrollTo(it) }
	}

	Column(modifier = modifier.fillMaxWidth()) {
		Text(
			text = stringResource(id = R.string.about_ack_title),
			style = MaterialTheme.typography.labelLarge,
			color = MaterialTheme.colorScheme.primary,
		)

		Spacer(modifier = Modifier.height(8.dp))

		Text(
			text = stringResource(id = R.string.about_ack_lead),
			style = MaterialTheme.typography.titleSmall,
		)

		Spacer(modifier = Modifier.height(5.dp))

		Text(
			text = stringResource(id = R.string.about_ack_credit),
			style = MaterialTheme.typography.bodySmall,
			fontSize = 13.sp,
			lineHeight = 21.sp,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)

		Spacer(modifier = Modifier.height(8.dp))

		Text(
			text = stringResource(id = R.string.about_ack_disclaimer),
			style = MaterialTheme.typography.bodySmall,
			fontSize = 13.sp,
			lineHeight = 21.sp,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)

		Spacer(modifier = Modifier.height(13.dp))

		FlowRow(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(5.dp),
		) {
			AssistChip(
				onClick = { onOpenUrl(UPSTREAM_REPO_URL) },
				label = { Text(stringResource(id = R.string.about_ack_upstream)) },
				leadingIcon = {
					Icon(
						imageVector = Icons.Default.Code,
						contentDescription = null,
						modifier = Modifier.size(AssistChipDefaults.IconSize),
					)
				},
			)
			AssistChip(
				onClick = { onOpenUrl(UPSTREAM_AUTHOR_URL) },
				label = { Text(stringResource(id = R.string.about_ack_author)) },
				leadingIcon = {
					Icon(
						imageVector = Icons.Default.Person,
						contentDescription = null,
						modifier = Modifier.size(AssistChipDefaults.IconSize),
					)
				},
			)
		}

		Spacer(modifier = Modifier.height(13.dp))

		Surface(
			onClick = { licenseExpanded = !licenseExpanded },
			shape = RoundedCornerShape(13.dp),
			color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
			modifier = Modifier.fillMaxWidth(),
		) {
			Column(modifier = Modifier.animateContentSize()) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = 48.dp)
						.padding(start = 13.dp, end = 5.dp),
					verticalAlignment = Alignment.CenterVertically,
				) {
					Text(
						text = stringResource(id = R.string.about_ack_license_toggle),
						style = MaterialTheme.typography.labelLarge,
						modifier = Modifier.weight(1f),
					)
					Icon(
						imageVector = Icons.Default.ExpandMore,
						contentDescription = stringResource(
							id = if (licenseExpanded) {
								R.string.about_ack_license_collapse_description
							} else {
								R.string.about_ack_license_expand_description
							}
						),
						modifier = Modifier
							.padding(8.dp)
							.rotate(chevronRotation),
					)
				}

				if (licenseExpanded) {
					SelectionContainer {
						Text(
							text = stringResource(id = R.string.about_ack_license),
							style = MaterialTheme.typography.bodySmall,
							fontSize = 13.sp,
							lineHeight = 21.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							modifier = Modifier.padding(
								start = 13.dp,
								end = 13.dp,
								bottom = 13.dp,
							),
						)
					}
				}
			}
		}
	}
}
