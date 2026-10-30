package com.legbehindneck.fasttrack48

import android.app.backup.BackupAgentHelper
import android.app.backup.SharedPreferencesBackupHelper

/**
 * Restores the key/value backups that earlier versions made. Those versions declared this
 * agent without android:fullBackupOnly, so Android kept their settings (the default shared
 * preferences) as a key/value set under [PREFS_BACKUP_KEY], and only an agent can read such
 * a set back. New backups are Auto Backup (fullBackupOnly="true" in the manifest, files per
 * backup_rules.xml / data_extraction_rules.xml): BackupAgent's own full-backup code makes
 * them, so this class only answers a restore of an old set.
 */
class FastTrackBackupAgent : BackupAgentHelper() {

	override fun onCreate() {
		super.onCreate()
		addHelper(PREFS_BACKUP_KEY, SharedPreferencesBackupHelper(this, "${packageName}_preferences"))
	}

	private companion object {
		/** The helper key earlier versions wrote under; a restore matches on it. */
		const val PREFS_BACKUP_KEY = "app_shared_prefs"
	}
}
