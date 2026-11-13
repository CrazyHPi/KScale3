package xyz.crazyh.kscale3.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import xyz.crazyh.kscale3.domain.BodyCompositionEstimates
import xyz.crazyh.kscale3.ui.i18n.LocalStrings

/** 2 × 3 体成分估算网格（结果页 / 记录详情共用，统一"估算值"语义）。 */
@Composable
fun EstimateGrid(
    estimates: BodyCompositionEstimates,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val tiles = listOf(
        EstimateTileData(
            strings.metricMuscleMass,
            estimates.muscleKg.rounded(2),
            "kg",
            Icons.Outlined.FitnessCenter,
        ),
        EstimateTileData(
            strings.metricBodyWater,
            estimates.tbwKg.rounded(2),
            "kg",
            Icons.Outlined.WaterDrop,
        ),
        EstimateTileData(
            strings.metricFatMass,
            estimates.fatMassKg.rounded(2),
            "kg",
            Icons.Outlined.Opacity,
        ),
        EstimateTileData(
            strings.metricBoneMass,
            estimates.boneKg.rounded(2),
            "kg",
            Icons.Outlined.AccessibilityNew,
        ),
        EstimateTileData(
            strings.metricBmi,
            estimates.bmi.rounded(1),
            "",
            Icons.Outlined.Straighten,
        ),
        EstimateTileData(
            strings.metricBmr,
            estimates.bmr.rounded(0),
            "kcal",
            Icons.Outlined.LocalFireDepartment,
        ),
    )
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { tile ->
                    EstimateTile(tile, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class EstimateTileData(
    val label: String,
    val value: String,
    val unit: String,
    val icon: ImageVector,
)

@Composable
private fun EstimateTile(data: EstimateTileData, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    data.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    data.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    data.value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (data.unit.isNotEmpty()) {
                    Text(
                        data.unit,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp, bottom = 1.dp),
                    )
                }
            }
        }
    }
}
