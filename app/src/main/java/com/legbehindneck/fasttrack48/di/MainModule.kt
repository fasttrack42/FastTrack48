package com.legbehindneck.fasttrack48.di

import androidx.room.Room
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastDataSource
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastPreferencesDataSource
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastRepository
import com.legbehindneck.fasttrack48.data.activefast.ActiveFastRepositoryImpl
import com.legbehindneck.fasttrack48.data.database.AppDatabase
import com.legbehindneck.fasttrack48.data.log.FastingLogDatabaseDatasource
import com.legbehindneck.fasttrack48.data.log.FastingLogDatasource
import com.legbehindneck.fasttrack48.data.log.FastingLogRepository
import com.legbehindneck.fasttrack48.data.log.FastingLogRepositoryImpl
import com.legbehindneck.fasttrack48.data.settings.SettingsDatasource
import com.legbehindneck.fasttrack48.data.settings.SettingsPreferencesDatasource
import com.legbehindneck.fasttrack48.screens.fasting.FastingViewModel
import com.legbehindneck.fasttrack48.screens.fasting.IFastingViewModel
import com.legbehindneck.fasttrack48.screens.log.ILogViewModel
import com.legbehindneck.fasttrack48.screens.log.LogViewModel
import com.legbehindneck.fasttrack48.screens.log.manualadd.IManualAddViewModel
import com.legbehindneck.fasttrack48.screens.log.manualadd.ManualAddViewModel
import com.legbehindneck.fasttrack48.screens.profile.IProfileViewModel
import com.legbehindneck.fasttrack48.screens.profile.ProfileViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

val mainModule = module {
	single {
		Room.databaseBuilder(
			get(),
			AppDatabase::class.java,
			"app-database"
		)
			.addMigrations(AppDatabase.MIGRATION_1_2)
			.build()
	}

	single { Clock.System } bind Clock::class

	singleOf(::SettingsPreferencesDatasource) bind SettingsDatasource::class

	singleOf(::ActiveFastPreferencesDataSource) bind ActiveFastDataSource::class
	singleOf(::ActiveFastRepositoryImpl) bind ActiveFastRepository::class

	singleOf(::FastingLogDatabaseDatasource) bind FastingLogDatasource::class
	singleOf(::FastingLogRepositoryImpl) bind FastingLogRepository::class

	viewModelOf(::FastingViewModel) bind IFastingViewModel::class
	viewModelOf(::LogViewModel) bind ILogViewModel::class
	viewModelOf(::ProfileViewModel) bind IProfileViewModel::class
	viewModelOf(::ManualAddViewModel) bind IManualAddViewModel::class
}
