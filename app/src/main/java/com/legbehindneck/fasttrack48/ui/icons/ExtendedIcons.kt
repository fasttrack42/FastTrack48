package com.legbehindneck.fasttrack48.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

// The extended Material icons the app uses, copied from
// androidx.compose.material:material-icons-extended 1.7.8 (Apache License 2.0, The Android Open
// Source Project) so the app depends on material-icons-core alone. Same names, same receivers
// and same lazily built vectors as the library's; only the import changes at the call sites.

val Icons.Filled.Code: ImageVector
	get() {
		_code?.let { return it }
		return materialIcon(name = "Filled.Code") {
			materialPath {
				moveTo(9.4f, 16.6f)
				lineTo(4.8f, 12f)
				lineToRelative(4.6f, -4.6f)
				lineTo(8f, 6f)
				lineToRelative(-6f, 6f)
				lineToRelative(6f, 6f)
				lineToRelative(1.4f, -1.4f)
				close()
				moveTo(14.6f, 16.6f)
				lineToRelative(4.6f, -4.6f)
				lineToRelative(-4.6f, -4.6f)
				lineTo(16f, 6f)
				lineToRelative(6f, 6f)
				lineToRelative(-6f, 6f)
				lineToRelative(-1.4f, -1.4f)
				close()
			}
		}.also { _code = it }
	}

private var _code: ImageVector? = null

val Icons.Filled.WarningAmber: ImageVector
	get() {
		_warningAmber?.let { return it }
		return materialIcon(name = "Filled.WarningAmber") {
			materialPath {
				moveTo(12f, 5.99f)
				lineTo(19.53f, 19f)
				horizontalLineTo(4.47f)
				lineTo(12f, 5.99f)
				moveTo(12f, 2f)
				lineTo(1f, 21f)
				horizontalLineToRelative(22f)
				lineTo(12f, 2f)
				lineTo(12f, 2f)
				close()
			}
			materialPath {
				moveTo(13f, 16f)
				lineToRelative(-2f, 0f)
				lineToRelative(0f, 2f)
				lineToRelative(2f, 0f)
				close()
			}
			materialPath {
				moveTo(13f, 10f)
				lineToRelative(-2f, 0f)
				lineToRelative(0f, 5f)
				lineToRelative(2f, 0f)
				close()
			}
		}.also { _warningAmber = it }
	}

private var _warningAmber: ImageVector? = null

val Icons.Filled.Language: ImageVector
	get() {
		_language?.let { return it }
		return materialIcon(name = "Filled.Language") {
			materialPath {
				moveTo(11.99f, 2f)
				curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
				reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
				curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
				reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
				close()
				moveTo(18.92f, 8f)
				horizontalLineToRelative(-2.95f)
				curveToRelative(-0.32f, -1.25f, -0.78f, -2.45f, -1.38f, -3.56f)
				curveToRelative(1.84f, 0.63f, 3.37f, 1.91f, 4.33f, 3.56f)
				close()
				moveTo(12f, 4.04f)
				curveToRelative(0.83f, 1.2f, 1.48f, 2.53f, 1.91f, 3.96f)
				horizontalLineToRelative(-3.82f)
				curveToRelative(0.43f, -1.43f, 1.08f, -2.76f, 1.91f, -3.96f)
				close()
				moveTo(4.26f, 14f)
				curveTo(4.1f, 13.36f, 4f, 12.69f, 4f, 12f)
				reflectiveCurveToRelative(0.1f, -1.36f, 0.26f, -2f)
				horizontalLineToRelative(3.38f)
				curveToRelative(-0.08f, 0.66f, -0.14f, 1.32f, -0.14f, 2f)
				curveToRelative(0f, 0.68f, 0.06f, 1.34f, 0.14f, 2f)
				lineTo(4.26f, 14f)
				close()
				moveTo(5.08f, 16f)
				horizontalLineToRelative(2.95f)
				curveToRelative(0.32f, 1.25f, 0.78f, 2.45f, 1.38f, 3.56f)
				curveToRelative(-1.84f, -0.63f, -3.37f, -1.9f, -4.33f, -3.56f)
				close()
				moveTo(8.03f, 8f)
				lineTo(5.08f, 8f)
				curveToRelative(0.96f, -1.66f, 2.49f, -2.93f, 4.33f, -3.56f)
				curveTo(8.81f, 5.55f, 8.35f, 6.75f, 8.03f, 8f)
				close()
				moveTo(12f, 19.96f)
				curveToRelative(-0.83f, -1.2f, -1.48f, -2.53f, -1.91f, -3.96f)
				horizontalLineToRelative(3.82f)
				curveToRelative(-0.43f, 1.43f, -1.08f, 2.76f, -1.91f, 3.96f)
				close()
				moveTo(14.34f, 14f)
				lineTo(9.66f, 14f)
				curveToRelative(-0.09f, -0.66f, -0.16f, -1.32f, -0.16f, -2f)
				curveToRelative(0f, -0.68f, 0.07f, -1.35f, 0.16f, -2f)
				horizontalLineToRelative(4.68f)
				curveToRelative(0.09f, 0.65f, 0.16f, 1.32f, 0.16f, 2f)
				curveToRelative(0f, 0.68f, -0.07f, 1.34f, -0.16f, 2f)
				close()
				moveTo(14.59f, 19.56f)
				curveToRelative(0.6f, -1.11f, 1.06f, -2.31f, 1.38f, -3.56f)
				horizontalLineToRelative(2.95f)
				curveToRelative(-0.96f, 1.65f, -2.49f, 2.93f, -4.33f, 3.56f)
				close()
				moveTo(16.36f, 14f)
				curveToRelative(0.08f, -0.66f, 0.14f, -1.32f, 0.14f, -2f)
				curveToRelative(0f, -0.68f, -0.06f, -1.34f, -0.14f, -2f)
				horizontalLineToRelative(3.38f)
				curveToRelative(0.16f, 0.64f, 0.26f, 1.31f, 0.26f, 2f)
				reflectiveCurveToRelative(-0.1f, 1.36f, -0.26f, 2f)
				horizontalLineToRelative(-3.38f)
				close()
			}
		}.also { _language = it }
	}

private var _language: ImageVector? = null

val Icons.Filled.FileUpload: ImageVector
	get() {
		_fileUpload?.let { return it }
		return materialIcon(name = "Filled.FileUpload") {
			materialPath {
				moveTo(9f, 16f)
				horizontalLineToRelative(6f)
				verticalLineToRelative(-6f)
				horizontalLineToRelative(4f)
				lineToRelative(-7f, -7f)
				lineToRelative(-7f, 7f)
				horizontalLineToRelative(4f)
				close()
				moveTo(5f, 18f)
				horizontalLineToRelative(14f)
				verticalLineToRelative(2f)
				lineTo(5f, 20f)
				close()
			}
		}.also { _fileUpload = it }
	}

private var _fileUpload: ImageVector? = null

val Icons.Filled.FileDownload: ImageVector
	get() {
		_fileDownload?.let { return it }
		return materialIcon(name = "Filled.FileDownload") {
			materialPath {
				moveTo(19f, 9f)
				horizontalLineToRelative(-4f)
				verticalLineTo(3f)
				horizontalLineTo(9f)
				verticalLineToRelative(6f)
				horizontalLineTo(5f)
				lineToRelative(7f, 7f)
				lineToRelative(7f, -7f)
				close()
				moveTo(5f, 18f)
				verticalLineToRelative(2f)
				horizontalLineToRelative(14f)
				verticalLineToRelative(-2f)
				horizontalLineTo(5f)
				close()
			}
		}.also { _fileDownload = it }
	}

private var _fileDownload: ImageVector? = null

val Icons.Filled.ExpandMore: ImageVector
	get() {
		_expandMore?.let { return it }
		return materialIcon(name = "Filled.ExpandMore") {
			materialPath {
				moveTo(16.59f, 8.59f)
				lineTo(12f, 13.17f)
				lineTo(7.41f, 8.59f)
				lineTo(6f, 10f)
				lineToRelative(6f, 6f)
				lineToRelative(6f, -6f)
				close()
			}
		}.also { _expandMore = it }
	}

private var _expandMore: ImageVector? = null

val Icons.Filled.DeleteSweep: ImageVector
	get() {
		_deleteSweep?.let { return it }
		return materialIcon(name = "Filled.DeleteSweep") {
			materialPath {
				moveTo(15f, 16f)
				horizontalLineToRelative(4f)
				verticalLineToRelative(2f)
				horizontalLineToRelative(-4f)
				close()
				moveTo(15f, 8f)
				horizontalLineToRelative(7f)
				verticalLineToRelative(2f)
				horizontalLineToRelative(-7f)
				close()
				moveTo(15f, 12f)
				horizontalLineToRelative(6f)
				verticalLineToRelative(2f)
				horizontalLineToRelative(-6f)
				close()
				moveTo(3f, 18f)
				curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
				horizontalLineToRelative(6f)
				curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
				lineTo(13f, 8f)
				lineTo(3f, 8f)
				verticalLineToRelative(10f)
				close()
				moveTo(14f, 5f)
				horizontalLineToRelative(-3f)
				lineToRelative(-1f, -1f)
				lineTo(6f, 4f)
				lineTo(5f, 5f)
				lineTo(2f, 5f)
				verticalLineToRelative(2f)
				horizontalLineToRelative(12f)
				close()
			}
		}.also { _deleteSweep = it }
	}

private var _deleteSweep: ImageVector? = null

val Icons.Filled.CalendarMonth: ImageVector
	get() {
		_calendarMonth?.let { return it }
		return materialIcon(name = "Filled.CalendarMonth") {
			materialPath {
				moveTo(19f, 4f)
				horizontalLineToRelative(-1f)
				verticalLineTo(2f)
				horizontalLineToRelative(-2f)
				verticalLineToRelative(2f)
				horizontalLineTo(8f)
				verticalLineTo(2f)
				horizontalLineTo(6f)
				verticalLineToRelative(2f)
				horizontalLineTo(5f)
				curveTo(3.89f, 4f, 3.01f, 4.9f, 3.01f, 6f)
				lineTo(3f, 20f)
				curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
				horizontalLineToRelative(14f)
				curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
				verticalLineTo(6f)
				curveTo(21f, 4.9f, 20.1f, 4f, 19f, 4f)
				close()
				moveTo(19f, 20f)
				horizontalLineTo(5f)
				verticalLineTo(10f)
				horizontalLineToRelative(14f)
				verticalLineTo(20f)
				close()
				moveTo(9f, 14f)
				horizontalLineTo(7f)
				verticalLineToRelative(-2f)
				horizontalLineToRelative(2f)
				verticalLineTo(14f)
				close()
				moveTo(13f, 14f)
				horizontalLineToRelative(-2f)
				verticalLineToRelative(-2f)
				horizontalLineToRelative(2f)
				verticalLineTo(14f)
				close()
				moveTo(17f, 14f)
				horizontalLineToRelative(-2f)
				verticalLineToRelative(-2f)
				horizontalLineToRelative(2f)
				verticalLineTo(14f)
				close()
				moveTo(9f, 18f)
				horizontalLineTo(7f)
				verticalLineToRelative(-2f)
				horizontalLineToRelative(2f)
				verticalLineTo(18f)
				close()
				moveTo(13f, 18f)
				horizontalLineToRelative(-2f)
				verticalLineToRelative(-2f)
				horizontalLineToRelative(2f)
				verticalLineTo(18f)
				close()
				moveTo(17f, 18f)
				horizontalLineToRelative(-2f)
				verticalLineToRelative(-2f)
				horizontalLineToRelative(2f)
				verticalLineTo(18f)
				close()
			}
		}.also { _calendarMonth = it }
	}

private var _calendarMonth: ImageVector? = null

val Icons.Filled.BarChart: ImageVector
	get() {
		_barChart?.let { return it }
		return materialIcon(name = "Filled.BarChart") {
			materialPath {
				moveTo(4f, 9f)
				horizontalLineToRelative(4f)
				verticalLineToRelative(11f)
				horizontalLineToRelative(-4f)
				close()
			}
			materialPath {
				moveTo(16f, 13f)
				horizontalLineToRelative(4f)
				verticalLineToRelative(7f)
				horizontalLineToRelative(-4f)
				close()
			}
			materialPath {
				moveTo(10f, 4f)
				horizontalLineToRelative(4f)
				verticalLineToRelative(16f)
				horizontalLineToRelative(-4f)
				close()
			}
		}.also { _barChart = it }
	}

private var _barChart: ImageVector? = null

val Icons.AutoMirrored.Filled.ViewList: ImageVector
	get() {
		_viewList?.let { return it }
		return materialIcon(name = "AutoMirrored.Filled.ViewList", autoMirror = true) {
			materialPath {
				moveTo(3f, 14f)
				horizontalLineToRelative(4f)
				verticalLineToRelative(-4f)
				horizontalLineTo(3f)
				verticalLineTo(14f)
				close()
				moveTo(3f, 19f)
				horizontalLineToRelative(4f)
				verticalLineToRelative(-4f)
				horizontalLineTo(3f)
				verticalLineTo(19f)
				close()
				moveTo(3f, 9f)
				horizontalLineToRelative(4f)
				verticalLineTo(5f)
				horizontalLineTo(3f)
				verticalLineTo(9f)
				close()
				moveTo(8f, 14f)
				horizontalLineToRelative(13f)
				verticalLineToRelative(-4f)
				horizontalLineTo(8f)
				verticalLineTo(14f)
				close()
				moveTo(8f, 19f)
				horizontalLineToRelative(13f)
				verticalLineToRelative(-4f)
				horizontalLineTo(8f)
				verticalLineTo(19f)
				close()
				moveTo(8f, 5f)
				verticalLineToRelative(4f)
				horizontalLineToRelative(13f)
				verticalLineTo(5f)
				horizontalLineTo(8f)
				close()
			}
		}.also { _viewList = it }
	}

private var _viewList: ImageVector? = null

val Icons.AutoMirrored.Filled.MenuBook: ImageVector
	get() {
		_menuBook?.let { return it }
		return materialIcon(name = "AutoMirrored.Filled.MenuBook", autoMirror = true) {
			materialPath {
				moveTo(21f, 5f)
				curveToRelative(-1.11f, -0.35f, -2.33f, -0.5f, -3.5f, -0.5f)
				curveToRelative(-1.95f, 0f, -4.05f, 0.4f, -5.5f, 1.5f)
				curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
				reflectiveCurveTo(2.45f, 4.9f, 1f, 6f)
				verticalLineToRelative(14.65f)
				curveToRelative(0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.5f)
				curveToRelative(0.1f, 0f, 0.15f, -0.05f, 0.25f, -0.05f)
				curveTo(3.1f, 20.45f, 5.05f, 20f, 6.5f, 20f)
				curveToRelative(1.95f, 0f, 4.05f, 0.4f, 5.5f, 1.5f)
				curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
				curveToRelative(1.65f, 0f, 3.35f, 0.3f, 4.75f, 1.05f)
				curveToRelative(0.1f, 0.05f, 0.15f, 0.05f, 0.25f, 0.05f)
				curveToRelative(0.25f, 0f, 0.5f, -0.25f, 0.5f, -0.5f)
				verticalLineTo(6f)
				curveTo(22.4f, 5.55f, 21.75f, 5.25f, 21f, 5f)
				close()
				moveTo(21f, 18.5f)
				curveToRelative(-1.1f, -0.35f, -2.3f, -0.5f, -3.5f, -0.5f)
				curveToRelative(-1.7f, 0f, -4.15f, 0.65f, -5.5f, 1.5f)
				verticalLineTo(8f)
				curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
				curveToRelative(1.2f, 0f, 2.4f, 0.15f, 3.5f, 0.5f)
				verticalLineTo(18.5f)
				close()
			}
			materialPath {
				moveTo(17.5f, 10.5f)
				curveToRelative(0.88f, 0f, 1.73f, 0.09f, 2.5f, 0.26f)
				verticalLineTo(9.24f)
				curveTo(19.21f, 9.09f, 18.36f, 9f, 17.5f, 9f)
				curveToRelative(-1.7f, 0f, -3.24f, 0.29f, -4.5f, 0.83f)
				verticalLineToRelative(1.66f)
				curveTo(14.13f, 10.85f, 15.7f, 10.5f, 17.5f, 10.5f)
				close()
			}
			materialPath {
				moveTo(13f, 12.49f)
				verticalLineToRelative(1.66f)
				curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
				curveToRelative(0.88f, 0f, 1.73f, 0.09f, 2.5f, 0.26f)
				verticalLineTo(11.9f)
				curveToRelative(-0.79f, -0.15f, -1.64f, -0.24f, -2.5f, -0.24f)
				curveTo(15.8f, 11.66f, 14.26f, 11.96f, 13f, 12.49f)
				close()
			}
			materialPath {
				moveTo(17.5f, 14.33f)
				curveToRelative(-1.7f, 0f, -3.24f, 0.29f, -4.5f, 0.83f)
				verticalLineToRelative(1.66f)
				curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
				curveToRelative(0.88f, 0f, 1.73f, 0.09f, 2.5f, 0.26f)
				verticalLineToRelative(-1.52f)
				curveTo(19.21f, 14.41f, 18.36f, 14.33f, 17.5f, 14.33f)
				close()
			}
		}.also { _menuBook = it }
	}

private var _menuBook: ImageVector? = null
