package br.com.vigiaterritorio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.vigiaterritorio.domain.model.AlertLevel
import br.com.vigiaterritorio.domain.model.KpiStatus

// Fonte única de verdade visual. Nenhuma tela usa cor, tamanho ou espaçamento solto.

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B5F7A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC2E8F5),
    onPrimaryContainer = Color(0xFF001F29),
    secondary = Color(0xFF3E6B4E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC0EDCB),
    onSecondaryContainer = Color(0xFF00210E),
    background = Color(0xFFF7FAFB),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFF7FAFB),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDCE4E8),
    onSurfaceVariant = Color(0xFF40484C),
    outline = Color(0xFF70787C),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FCFEA),
    onPrimary = Color(0xFF003545),
    primaryContainer = Color(0xFF004D63),
    onPrimaryContainer = Color(0xFFC2E8F5),
    secondary = Color(0xFFA3D1B0),
    onSecondary = Color(0xFF0B3820),
    secondaryContainer = Color(0xFF265237),
    onSecondaryContainer = Color(0xFFC0EDCB),
    background = Color(0xFF101416),
    onBackground = Color(0xFFE0E3E4),
    surface = Color(0xFF101416),
    onSurface = Color(0xFFE0E3E4),
    surfaceVariant = Color(0xFF40484C),
    onSurfaceVariant = Color(0xFFC0C8CC),
    outline = Color(0xFF8A9296),
    error = Color(0xFFFFB4AB),
)

/** Cores semânticas de risco. Contraste ≥ 4,5:1 com [StatusColors.onStatus]. */
@Immutable
data class StatusColors(
    val normal: Color,
    val info: Color,
    val attention: Color,
    val critical: Color,
    val neutral: Color,
    val onStatus: Color,
) {
    fun of(level: AlertLevel): Color = when (level) {
        AlertLevel.INFORMATIVO -> info
        AlertLevel.ATENCAO -> attention
        AlertLevel.CRITICO -> critical
    }

    fun of(status: KpiStatus): Color = when (status) {
        KpiStatus.NORMAL -> normal
        KpiStatus.ATENCAO -> attention
        KpiStatus.CRITICO -> critical
        KpiStatus.SEM_DADOS -> neutral
    }
}

private val LightStatus = StatusColors(
    normal = Color(0xFF2E7D32),
    info = Color(0xFF0B5F7A),
    attention = Color(0xFF8A5A00),
    critical = Color(0xFFB3261E),
    neutral = Color(0xFF5B6367),
    onStatus = Color.White,
)

private val DarkStatus = StatusColors(
    normal = Color(0xFF81C995),
    info = Color(0xFF7FCFEA),
    attention = Color(0xFFFFB95C),
    critical = Color(0xFFFFB4AB),
    neutral = Color(0xFFC0C8CC),
    onStatus = Color(0xFF101416),
)

/** Grade de 4 dp. */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
)

/** Escala enxuta: seis níveis. */
private val VigiaTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val VigiaShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatus }
private val LocalSpacing = staticCompositionLocalOf { Spacing() }

object VigiaTheme {
    val status: StatusColors
        @Composable get() = LocalStatusColors.current
    val spacing: Spacing
        @Composable get() = LocalSpacing.current
}

@Composable
fun VigiaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = VigiaTypography,
            shapes = VigiaShapes,
            content = content,
        )
    }
}
