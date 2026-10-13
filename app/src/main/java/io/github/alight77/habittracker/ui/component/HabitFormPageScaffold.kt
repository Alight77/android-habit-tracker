package io.github.alight77.habittracker.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.alight77.habittracker.R
import io.github.alight77.habittracker.ui.theme.WarmPaperBottom
import io.github.alight77.habittracker.ui.theme.WarmPaperMiddle
import io.github.alight77.habittracker.ui.theme.WarmPaperTop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitFormPageScaffold(
    title: String,
    backgroundIllustrationRes: Int,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val isLight = colors.background.luminance() > 0.5f
    val background = if (isLight) {
        Brush.verticalGradient(
            0f to WarmPaperTop,
            0.50f to WarmPaperMiddle,
            0.74f to colors.background,
            1f to WarmPaperBottom
        )
    } else {
        Brush.verticalGradient(listOf(colors.background, colors.surface))
    }
    val backDescription = stringResource(R.string.back)

    Box(Modifier.fillMaxSize().background(background)) {
        if (isLight) {
            // 保留上部与中部叶片；下方渐隐到主题底色，避免底部装饰干扰表单和保存按钮。
            Image(
                painter = painterResource(backgroundIllustrationRes),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (LocalDensity.current.fontScale >= 1.5f) 0.12f else 0.28f)
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.White,
                                0.60f to Color.White,
                                0.82f to Color.Transparent,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            )
        }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = colors.onBackground,
                        navigationIconContentColor = colors.onBackground
                    ),
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.semantics { contentDescription = backDescription }
                        ) {
                            Text("‹")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            content = content
        )
    }
}

@Composable
fun HabitFormHero(
    title: String,
    guidance: String,
    illustrationRes: Int
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 与统计页一致：窄屏、放大字体和深色模式下让文字独占宽度。
        val showIllustration = maxWidth >= 340.dp &&
            colors.background.luminance() > 0.5f &&
            LocalDensity.current.fontScale < 1.5f
        Box(Modifier.fillMaxWidth().heightIn(min = if (showIllustration) 170.dp else 96.dp)) {
            if (showIllustration) {
                Image(
                    painter = painterResource(illustrationRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.CenterEnd).width(172.dp).height(160.dp)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(if (showIllustration) 0.53f else 1f)
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onBackground
                )
                Text(
                    text = guidance,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Serif,
                    color = colors.onSurfaceVariant
                )
            }
        }
    }
}
