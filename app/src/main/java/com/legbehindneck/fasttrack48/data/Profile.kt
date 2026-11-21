package com.legbehindneck.fasttrack48.data

import java.io.Serializable

data class Profile(
		val ageYears: Int = 0,
		val heightCm: Double = 0.0,
		val weightKg: Double = 0.0,
		// Unset until the user picks one: a preselected sex would be a choice made for them,
		// and a wrong BMR and safety notes. Nullable in Kotlin only; the serialized field is
		// the same reference type, so stored profiles read back unchanged.
		val gender: Gender? = null): Serializable
{
	companion object
	{
		private const val serialVersionUID = 8712361231924L
	}

	fun isValid() = ageYears > 0 && heightCm > 0.0 && weightKg > 0.0
}

enum class Gender
{ Male, Female }