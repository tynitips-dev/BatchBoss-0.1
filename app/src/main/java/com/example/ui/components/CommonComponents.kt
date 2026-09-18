package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

@Composable
fun BatchBossBottomNav(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                iconFilled = Icons.Filled.Home,
                iconOutlined = Icons.Outlined.Home,
                label = "Home",
                isSelected = selectedTab == 0,
                testTag = "nav_home",
                onClick = { onTabSelected(0) }
            )
            NavItem(
                iconFilled = Icons.Filled.MenuBook,
                iconOutlined = Icons.Outlined.MenuBook,
                label = "Recipes",
                isSelected = selectedTab == 1,
                testTag = "nav_recipes",
                onClick = { onTabSelected(1) }
            )
            NavItem(
                iconFilled = Icons.Filled.Kitchen,
                iconOutlined = Icons.Outlined.Kitchen,
                label = "Ingredients",
                isSelected = selectedTab == 2,
                testTag = "nav_ingredients",
                onClick = { onTabSelected(2) }
            )
            NavItem(
                iconFilled = Icons.Filled.Storefront,
                iconOutlined = Icons.Outlined.Storefront,
                label = "Suppliers",
                isSelected = selectedTab == 3,
                testTag = "nav_suppliers",
                onClick = { onTabSelected(3) }
            )
            NavItem(
                iconFilled = Icons.Filled.Widgets,
                iconOutlined = Icons.Outlined.Widgets,
                label = "More",
                isSelected = selectedTab == 4,
                testTag = "nav_more",
                onClick = { onTabSelected(4) }
            )
        }
    }
}

@Composable
private fun NavItem(
    iconFilled: androidx.compose.ui.graphics.vector.ImageVector,
    iconOutlined: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isSelected) iconFilled else iconOutlined,
            contentDescription = label,
            tint = if (isSelected) BatchPink else LightText,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) BatchPink else LightText
        )
    }
}

@Composable
fun BatchBossEmblem(
    modifier: Modifier = Modifier,
    size: Int = 36
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_batchboss_emblem),
            contentDescription = "BatchBoss Emblem",
            modifier = Modifier.size(size.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun BatchBossBrandLogo(
    modifier: Modifier = Modifier,
    size: Int = 36,
    showVersionBadge: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        BatchBossEmblem(size = size)
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Batch",
                fontSize = (size * 0.58).sp,
                fontWeight = FontWeight.ExtraBold,
                color = DarkText
            )
            Text(
                text = "Boss",
                fontSize = (size * 0.58).sp,
                fontWeight = FontWeight.ExtraBold,
                color = BatchPink
            )
            if (showVersionBadge) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = BatchPinkLight,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, BatchPink.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "v7",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BatchPink,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BatchBossFullLogo(
    modifier: Modifier = Modifier,
    size: Int = 200,
    showCardBackground: Boolean = false
) {
    if (showCardBackground) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_batchboss_logo),
                contentDescription = "BatchBoss Logo - Cost Price Profit",
                modifier = Modifier
                    .size(size.dp)
                    .padding(12.dp),
                contentScale = ContentScale.Fit
            )
        }
    } else {
        Image(
            painter = painterResource(id = R.drawable.img_batchboss_logo),
            contentDescription = "BatchBoss Logo - Cost Price Profit",
            modifier = modifier
                .size(size.dp),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun ProfitSparkline(
    modifier: Modifier = Modifier,
    lineColor: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val path = Path()
        val points = listOf(
            Offset(0f, size.height * 0.7f),
            Offset(size.width * 0.2f, size.height * 0.8f),
            Offset(size.width * 0.4f, size.height * 0.4f),
            Offset(size.width * 0.6f, size.height * 0.5f),
            Offset(size.width * 0.8f, size.height * 0.25f),
            Offset(size.width, size.height * 0.15f)
        )

        path.moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val controlX = (p0.x + p1.x) / 2
            path.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
        }

        drawPath(
            path = path,
            color = lineColor.copy(alpha = 0.85f),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun ProfitDonutChart(
    ingredientsPercent: Float,
    labourPercent: Float,
    overheadsPercent: Float,
    profitPercent: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 24.dp.toPx()
            val canvasSize = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - canvasSize) / 2, (size.height - canvasSize) / 2)
            val arcSize = Size(canvasSize, canvasSize)

            var startAngle = -90f

            // Ingredients slice (Warm Amber)
            val angle1 = (ingredientsPercent / 100f) * 360f
            drawArc(
                color = CoralOrange,
                startAngle = startAngle,
                sweepAngle = angle1 - 3f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += angle1

            // Labour slice (Yellow/Amber)
            val angle2 = (labourPercent / 100f) * 360f
            drawArc(
                color = WarmAmber,
                startAngle = startAngle,
                sweepAngle = angle2 - 3f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += angle2

            // Overheads slice (Purple)
            val angle3 = (overheadsPercent / 100f) * 360f
            drawArc(
                color = PurpleAccent,
                startAngle = startAngle,
                sweepAngle = angle3 - 3f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += angle3

            // Profit slice (Green)
            val angle4 = (profitPercent / 100f) * 360f
            drawArc(
                color = MintGreen,
                startAngle = startAngle,
                sweepAngle = angle4 - 3f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Profit",
                fontSize = 12.sp,
                color = LightText
            )
            Text(
                text = "${String.format("%.1f", profitPercent)}%",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )
        }
    }
}
