package com.legbehindneck.fasttrack48.screens.settings

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.knowledge.ConditionGroup
import com.legbehindneck.fasttrack48.data.knowledge.HealthCondition
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeCategory
import com.legbehindneck.fasttrack48.data.knowledge.KnowledgeStore

/**
 * Settings › Knowledge: five on-by-default categories, women's physiology, health conditions
 * (off; switching it on reveals the condition chips), the tip chip and evidence labels.
 * Safety essentials have no switch; a note says so.
 */
@Composable
fun KnowledgeSettingsSection() {
	val context = LocalContext.current
	val state by KnowledgeStore.state(context).collectAsState()
	val s = state.settings

	Column {
		listOf(
			KnowledgeCategory.Journey, KnowledgeCategory.Mind, KnowledgeCategory.Food,
			KnowledgeCategory.Rhythm, KnowledgeCategory.Comfort, KnowledgeCategory.Women,
			KnowledgeCategory.Medical,
		).forEach { category ->
			ToggleRow(
				headline = category.title,
				details = category.subtitle,
				value = category in s.enabled,
				onChange = { KnowledgeStore.setCategory(context, category, it) },
			)
			if (category == KnowledgeCategory.Medical && category in s.enabled) {
				ConditionChips(
					selected = s.conditions,
					onToggle = { c -> KnowledgeStore.setCondition(context, c, c !in s.conditions) },
				)
			}
		}
		ToggleRow(
			headline = R.string.kb_settings_tips_title,
			details = R.string.kb_settings_tips_subtitle,
			value = s.tipsDuringFasts,
			onChange = { KnowledgeStore.setTipsDuringFasts(context, it) },
		)
		ToggleRow(
			headline = R.string.kb_settings_labels_title,
			details = R.string.kb_settings_labels_subtitle,
			value = s.evidenceLabels,
			onChange = { KnowledgeStore.setEvidenceLabels(context, it) },
		)
		ListItem(
			modifier = Modifier.clickable {
				KnowledgeStore.resetSeen(context)
				Toast.makeText(context, R.string.kb_settings_reset_done, Toast.LENGTH_SHORT).show()
			},
			headlineContent = {
				Text(
					text = stringResource(R.string.kb_settings_reset_title),
					style = MaterialTheme.typography.labelLarge,
					fontWeight = FontWeight.Bold,
				)
			},
			supportingContent = {
				Text(text = stringResource(R.string.kb_settings_reset_subtitle), style = MaterialTheme.typography.bodySmall)
			},
		)
		Text(
			text = stringResource(R.string.kb_settings_essentials_note),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
		)
	}
}

@Composable
private fun ToggleRow(@StringRes headline: Int, @StringRes details: Int, value: Boolean, onChange: (Boolean) -> Unit) {
	ListItem(
		modifier = Modifier.clickable { onChange(!value) },
		headlineContent = {
			Text(
				text = stringResource(headline),
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.Bold,
			)
		},
		supportingContent = {
			Text(text = stringResource(details), style = MaterialTheme.typography.bodySmall)
		},
		trailingContent = { Switch(checked = value, onCheckedChange = onChange) },
	)
}

/** Grouped so conditions are recognised at a glance rather than recalled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionChips(selected: Set<HealthCondition>, onToggle: (HealthCondition) -> Unit) {
	Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
		Text(
			text = stringResource(R.string.kb_settings_conditions_hint),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
		)
		ConditionGroup.entries.forEach { group ->
			Text(
				text = stringResource(group.title),
				style = MaterialTheme.typography.labelLarge,
				fontWeight = FontWeight.Bold,
				modifier = Modifier.padding(top = 12.dp),
			)
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				HealthCondition.entries.filter { it.group == group }.forEach { condition ->
					FilterChip(
						selected = condition in selected,
						onClick = { onToggle(condition) },
						label = { Text(stringResource(condition.label)) },
					)
				}
			}
		}
	}
}
