package com.legbehindneck.fasttrack48.screens.profile

import android.content.Context
import android.os.LocaleList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cafe.adriel.satchel.Satchel
import cafe.adriel.satchel.ktx.getOrSet
import com.legbehindneck.fasttrack48.R
import com.legbehindneck.fasttrack48.data.Data
import com.legbehindneck.fasttrack48.data.Gender
import com.legbehindneck.fasttrack48.data.Profile
import com.legbehindneck.fasttrack48.data.settings.SettingsDatasource
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt

// Accepted ranges: about twice the usual adult spread, wide enough for anyone and narrow
// enough to catch a slipped digit. Each imperial range spans about the same as its metric one.
private val HEIGHT_CM = 60..300
private val HEIGHT_FEET = 2..9
private val HEIGHT_INCHES = 0..11
private val WEIGHT_KG = 20..500
private val WEIGHT_LBS = 45..1100
private val AGE_YEARS = 10..130

class ProfileViewModel(
    private val appContext: Context,
    private val settings: SettingsDatasource,
) : ViewModel(), IProfileViewModel {

    private val _uiState = MutableStateFlow(IProfileViewModel.ProfileUiState())
    override val uiState: StateFlow<IProfileViewModel.ProfileUiState> = _uiState.asStateFlow()

    // onCreate runs from a LaunchedEffect each time the screen enters composition; one
    // collector for the life of the view model, not one per entry.
    private var started = false

    override fun onCreate() {
        if (started) return
        started = true
        viewModelScope.launch {
            settings.useMetricSystemFlow(default = isMetricSystemLocale()).collectLatest { isMetric ->
                val profile = Satchel.storage.getOrSet(Data.KEY_PROFILE, Profile())
                populateUiState(profile, isMetric)
            }
        }
    }

    private fun populateUiState(profile: Profile, isMetric: Boolean) {
        // Rounded, not floored: 5 ft 2 in is stored as 157.48 cm, which converts back to
        // 61.99999… inches and would otherwise come back as 5 ft 1 in.
        val totalInches = Data.cmToInch(profile.heightCm).roundToInt()
        val feet = totalInches / 12
        val inches = totalInches % 12

        val totalCmStr = if (profile.heightCm == 0.0) "" else profile.heightCm.roundToInt().toString()
        val feetStr = if (profile.heightCm == 0.0) "" else feet.toString()
        val inchesStr = if (profile.heightCm == 0.0) "" else inches.toString()

        val pounds = Data.kgToLbs(profile.weightKg)

        val weightPoundsStr = if (profile.weightKg == 0.0) "" else "%.01f".format(pounds)
        val weightKgStr = if (profile.weightKg == 0.0) "" else "%.01f".format(profile.weightKg)

        val (bmiValue, bmrValue) = derivedValues(profile)

        _uiState.update {
            it.copy(
                isMetric = isMetric,
                heightCm = totalCmStr,
                heightFeet = feetStr,
                heightInches = inchesStr,
                weightKg = weightKgStr,
                weightLbs = weightPoundsStr,
                age = if (profile.ageYears == 0) "" else profile.ageYears.toString(),
                gender = profile.gender,
                bmiValue = bmiValue,
                bmrValue = bmrValue
            )
        }
    }

    /** BMI and BMR text for [profile]; both empty until the profile is complete. */
    private fun derivedValues(profile: Profile): Pair<String, String> {
        // Only surface BMI/BMR once the profile is complete; an empty profile
        // has a BMI of 0.0, which would otherwise read as "Underweight".
        val bmiValue: String
        val bmrValue: String
        if (profile.isValid()) {
            val bmi = calculateBmi(profile)
            val bmiCategory = when {
                bmi < 18.5 -> appContext.getString(R.string.profile_bmi_category_underweight)
                bmi < 25.0 -> appContext.getString(R.string.profile_bmi_category_normal)
                bmi < 30.0 -> appContext.getString(R.string.profile_bmi_category_overweight)
                bmi < 40.0 -> appContext.getString(R.string.profile_bmi_category_obese)
                else -> appContext.getString(R.string.profile_bmi_category_morbidly_obese)
            }
            bmiValue = appContext.getString(R.string.profile_bmi_value, bmi, bmiCategory)
            bmrValue = appContext.getString(R.string.profile_bmr_value, calculateBmr(profile))
        } else {
            bmiValue = ""
            bmrValue = ""
        }
        return bmiValue to bmrValue
    }

    override fun updateHeightCm(value: String) {
        _uiState.update { it.copy(heightCm = value) }
        validateAndSaveProfile()
    }

    override fun updateHeightFeet(value: String) {
        _uiState.update { it.copy(heightFeet = value) }
        validateAndSaveProfile()
    }

    override fun updateHeightInches(value: String) {
        _uiState.update { it.copy(heightInches = value) }
        validateAndSaveProfile()
    }

    override fun updateWeightKg(value: String) {
        _uiState.update { it.copy(weightKg = value) }
        validateAndSaveProfile()
    }

    override fun updateWeightLbs(value: String) {
        _uiState.update { it.copy(weightLbs = value) }
        validateAndSaveProfile()
    }

    override fun updateAge(value: String) {
        _uiState.update { it.copy(age = value) }
        validateAndSaveProfile()
    }

    override fun updateGender(value: Gender) {
        _uiState.update { it.copy(gender = value) }
        validateAndSaveProfile()
    }

    private fun validateAndSaveProfile() {
        if (validateProfile()) {
            saveProfile()
        }
    }

    private fun validateProfile(): Boolean {
        val state = _uiState.value
        val metric = state.isMetric
        val heightCmError = if (metric) decimalError(state.heightCm, HEIGHT_CM) else null
        val heightFeetError = if (metric) null else wholeError(state.heightFeet, HEIGHT_FEET)
        val heightInchesError = if (metric) null else wholeError(state.heightInches, HEIGHT_INCHES)
        val weightKgError = if (metric) decimalError(state.weightKg, WEIGHT_KG) else null
        val weightLbsError = if (metric) null else decimalError(state.weightLbs, WEIGHT_LBS)
        val ageError = wholeError(state.age, AGE_YEARS)

        _uiState.update {
            it.copy(
                heightCmError = heightCmError,
                heightFeetError = heightFeetError,
                heightInchesError = heightInchesError,
                weightKgError = weightKgError,
                weightLbsError = weightLbsError,
                ageError = ageError
            )
        }

        return heightCmError == null && heightFeetError == null && heightInchesError == null &&
            weightKgError == null && weightLbsError == null && ageError == null
    }

    /** Null when [text] is a number in [range]; otherwise the message that names the range. */
    private fun decimalError(text: String, range: IntRange): String? {
        val value = parseDecimal(text)
        // NaN fails both comparisons, so "NaN" is reported like any other bad entry.
        return if (value != null && value >= range.first && value <= range.last) null
        else appContext.getString(R.string.profile_error_range, range.first, range.last)
    }

    /** Null when [text] is a whole number in [range]; otherwise the message that names the range. */
    private fun wholeError(text: String, range: IntRange): String? =
        if (parseWhole(text)?.let { it in range } == true) null
        else appContext.getString(R.string.profile_error_range_whole, range.first, range.last)

    private fun saveProfile() {
        val profile = createProfileFromUiState()
        if (profile != null) {
            Satchel.storage.apply {
                set(Data.KEY_PROFILE, profile)
            }
            // Refresh only what is derived. Re-formatting the fields from the saved profile
            // would rewrite the text under the user's cursor on every valid keystroke — "72."
            // parses to 72 and comes back as "72.0", so a decimal could never be typed.
            val (bmiValue, bmrValue) = derivedValues(profile)
            _uiState.update { it.copy(bmiValue = bmiValue, bmrValue = bmrValue) }
            Napier.i("Profile saved")
        }
    }

    /** The profile the fields describe; null unless every field parses. Call after validation. */
    private fun createProfileFromUiState(): Profile? {
        val state = _uiState.value

        val totalCm: Double
        val kg: Double

        if (state.isMetric) {
            totalCm = parseDecimal(state.heightCm) ?: return null
            kg = parseDecimal(state.weightKg) ?: return null
        } else {
            val feet = parseWhole(state.heightFeet) ?: return null
            val inches = parseWhole(state.heightInches) ?: return null
            totalCm = Data.inchToCm(feet * 12 + inches)
            kg = Data.lbsToKg(parseDecimal(state.weightLbs) ?: return null)
        }

        val age = parseWhole(state.age) ?: return null

        return Profile(
            ageYears = age,
            heightCm = totalCm,
            weightKg = kg,
            gender = state.gender
        ).takeIf { it.isValid() }
    }

    private fun calculateBmi(profile: Profile): Double {
        return if (profile.isValid()) {
            // weight (kg) / [height (m)]2
            val heightM = profile.heightCm / 100
            profile.weightKg / (heightM.pow(2.0))
        } else {
            0.0
        }
    }

    private fun calculateBmr(profile: Profile): Double {
        return when (profile.gender) {
            //BMR for Men = 66.47 + (13.75 * weight [kg]) + (5.003 * size [cm]) − (6.755 * age [years])
            Gender.Male -> {
                66.47 + (13.75 * profile.weightKg) + (5.003 * profile.heightCm) - (6.755 * profile.ageYears)
            }
            //BMR for Women = 655.1 + (9.563 * weight [kg]) + (1.85 * size [cm]) − (4.676 * age [years])
            Gender.Female -> {
                655.1 + (9.563 * profile.weightKg) + (1.85 * profile.heightCm) - (4.676 * profile.ageYears)
            }
        }
    }

    private fun parseWhole(text: String): Int? = text.trim().toIntOrNull()

    /**
     * [text] as a number, written either as "72.5" or in the locale's own form ("72,5"). Null
     * unless the number is the whole text, so "72kg" is not quietly read as 72.
     */
    private fun parseDecimal(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toDoubleOrNull()?.let { return it }
        val position = ParsePosition(0)
        val number = NumberFormat.getInstance().parse(trimmed, position) ?: return null
        return if (position.index == trimmed.length) number.toDouble() else null
    }

    private fun isMetricSystemLocale(): Boolean {
        val locale: Locale = LocaleList.getDefault()[0]
        val imperialCountries = listOf("US", "LR", "MM")
        return !imperialCountries.contains(locale.country)
    }
}
