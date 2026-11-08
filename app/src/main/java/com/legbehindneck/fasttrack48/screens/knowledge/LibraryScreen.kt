package com.legbehindneck.fasttrack48.screens.knowledge

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeCategory
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeSelector
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeStore
import com.legbehindneck.fasttrack48.utils.MAX_COLUMN_WIDTH

/**
 * The library: every card the reader's settings allow, in reading order, with search and a
 * "Can I have…?" lookup first, because that's the question people bring most often. Replaces
 * the old Info / Tips markdown pages.
 */
@Composable
fun LibraryScreen(paddingValues: PaddingValues, onOpenSources: () -> Unit, modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val ctx = rememberKnowledgeContext()
	val profile = rememberKnowledgeProfile()
	val labels = ctx.state.settings.evidenceLabels
	var query by rememberSaveable { mutableStateOf("") }

	val sections = remember(ctx) { KnowledgeSelector.library(ctx) }
	val drinks = remember(ctx) { KnowledgeSelector.byIds(KnowledgeSelector.DrinkLookup, ctx) }
	val results = remember(query, ctx) { KnowledgeSelector.search(query, ctx) { context.getString(it) } }
	val offerWomen = KnowledgeSelector.offerWomen(ctx, profile)

	Box(modifier = modifier.fillMaxSize()) {
		LazyColumn(
			modifier = Modifier
				.widthIn(max = MAX_COLUMN_WIDTH)
				.fillMaxHeight()
				.align(Alignment.TopCenter),
			contentPadding = PaddingValues(
				start = 16.dp,
				end = 16.dp,
				top = paddingValues.calculateTopPadding() + 8.dp,
				bottom = paddingValues.calculateBottomPadding() + 24.dp,
			),
			verticalArrangement = Arrangement.spacedBy(8.dp),
		) {
			item(key = "search") {
				OutlinedTextField(
					value = query,
					onValueChange = { query = it },
					modifier = Modifier.fillMaxWidth(),
					singleLine = true,
					placeholder = { Text(stringResource(R.string.kb_search_hint)) },
					leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
					trailingIcon = {
						if (query.isNotEmpty()) {
							IconButton(onClick = { query = "" }) {
								Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.kb_clear_search))
							}
						}
					},
				)
			}

			if (query.isNotBlank()) {
				if (results.isEmpty()) {
					item(key = "no_results") {
						Text(
							text = stringResource(R.string.kb_no_results),
							style = MaterialTheme.typography.bodyMedium,
							modifier = Modifier.padding(vertical = 16.dp),
						)
					}
				}
				items(results, key = { "r_" + it.id }) { KnowledgeCardView(it, labels) }
			} else {
				if (offerWomen) {
					item(key = "women_offer") {
						WomenOffer(
							onAccept = {
								KnowledgeStore.setCategory(context, KnowledgeCategory.Women, true)
								KnowledgeStore.markWomenOfferShown(context)
							},
							onDecline = { KnowledgeStore.markWomenOfferShown(context) },
							showLabel = labels,
						)
					}
				}
				if (drinks.isNotEmpty()) {
					item(key = "drinks_header") { SectionHeader(R.string.kb_can_i_have, null) }
					items(drinks, key = { "d_" + it.id }) { KnowledgeCardView(it, labels) }
				}
				sections.forEach { (category, cards) ->
					item(key = "h_" + category.id) { SectionHeader(category.title, category.subtitle) }
					items(cards, key = { category.id + "_" + it.id }) { KnowledgeCardView(it, labels) }
				}
				item(key = "footer") {
					Column(
						modifier = Modifier
							.fillMaxWidth()
							.padding(top = 16.dp),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.spacedBy(8.dp),
					) {
						Text(
							text = stringResource(R.string.kb_more_topics),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center,
						)
						Text(
							text = stringResource(R.string.kb_sources_medical),
							style = MaterialTheme.typography.bodySmall,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center,
						)
						TextButton(onClick = onOpenSources) {
							Text(text = stringResource(R.string.kb_sources_title))
						}
					}
				}
			}
		}
	}
}

@Composable
private fun SectionHeader(@StringRes title: Int, @StringRes subtitle: Int?) {
	Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
		Text(
			text = stringResource(title),
			style = MaterialTheme.typography.titleLarge,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.primary,
		)
		if (subtitle != null) {
			Text(
				text = stringResource(subtitle),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
	}
}

/** Offered once to readers who set female in Profile (INTEGRATION.md §3F). */
@Composable
private fun WomenOffer(onAccept: () -> Unit, onDecline: () -> Unit, showLabel: Boolean) {
	Surface(
		shape = RoundedCornerShape(16.dp),
		color = MaterialTheme.colorScheme.secondaryContainer,
		modifier = Modifier.fillMaxWidth(),
	) {
		Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text(
				text = stringResource(R.string.kb_women_offer_title),
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold,
			)
			Text(text = stringResource(R.string.kb_women_offer_body), style = MaterialTheme.typography.bodyMedium)
			KnowledgeSelector.periodStopsCard()?.let { KnowledgeCardView(it, showLabel) }
			Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
				TextButton(onClick = onDecline) { Text(stringResource(R.string.kb_women_offer_no)) }
				Button(onClick = onAccept) { Text(stringResource(R.string.kb_women_offer_yes)) }
			}
		}
	}
}
