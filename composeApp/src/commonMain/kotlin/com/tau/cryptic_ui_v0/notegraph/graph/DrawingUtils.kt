package com.tau.cryptic_ui_v0.notegraph.graph

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.*
// Import the default constants
import com.tau.cryptic_ui_v0.notegraph.graph.DEFAULT_EDGE_ARROW_ANGLE_RAD
import com.tau.cryptic_ui_v0.notegraph.graph.DEFAULT_EDGE_ARROW_LENGTH

// Calculate intersection points for edges
fun calculateEdgeEndpoints(fromPos: Offset, toPos: Offset, fromRadius: Float, toRadius: Float): Pair<Offset, Offset> {
    val dx = toPos.x - fromPos.x
    val dy = toPos.y - fromPos.y
    val distSq = dx * dx + dy * dy

    if (distSq <= (fromRadius + toRadius).pow(2) || distSq == 0f) {
        return Pair(fromPos, toPos)
    }

    val dist = sqrt(distSq)
    val ratioFrom = fromRadius / dist
    val ratioTo = toRadius / dist

    val startX = fromPos.x + dx * ratioFrom
    val startY = fromPos.y + dy * ratioFrom
    val endX = toPos.x - dx * ratioTo
    val endY = toPos.y - dy * ratioTo

    return Pair(Offset(startX, startY), Offset(endX, endY))
}


// Draw Arrow Head
fun drawArrowHead(drawScope: DrawScope, startPos: Offset, endPos: Offset, color: Color, viewScale: Float) {
    val dx = endPos.x - startPos.x
    val dy = endPos.y - startPos.y
    val angle = atan2(dy, dx)
    // Use the default constant
    val headLength = DEFAULT_EDGE_ARROW_LENGTH / viewScale

    // Use the default constant
    val angle1 = angle + DEFAULT_EDGE_ARROW_ANGLE_RAD
    val angle2 = angle - DEFAULT_EDGE_ARROW_ANGLE_RAD

    val arrowX1 = endPos.x - headLength * cos(angle1)
    val arrowY1 = endPos.y - headLength * sin(angle1)
    val arrowX2 = endPos.x - headLength * cos(angle2)
    val arrowY2 = endPos.y - headLength * sin(angle2)

    val path = Path().apply {
        moveTo(endPos.x, endPos.y)
        lineTo(arrowX1, arrowY1)
        lineTo(arrowX2, arrowY2)
        close()
    }
    drawScope.drawPath(path, color = color)
}

// Draw Self Loop
fun drawSelfLoop(drawScope: DrawScope, nodePos: Offset, nodeRadius: Float, color: Color, strokeWidth: Float) {
    val loopRadius = SELF_LOOP_RADIUS
    val controlOffsetAngle = SELF_LOOP_OFFSET_ANGLE

    val centerAngle = controlOffsetAngle - (PI / 2f).toFloat()
    val centerDist = nodeRadius + loopRadius
    val loopCenterX = nodePos.x + centerDist * cos(centerAngle)
    val loopCenterY = nodePos.y + centerDist * sin(centerAngle)

    val angleNodeToLoopCenter = atan2(loopCenterY - nodePos.y, loopCenterX - nodePos.x)
    val acosArg = (nodeRadius / centerDist).coerceIn(-1f, 1f)
    val angleOffsetToTouchPoint = acos(acosArg)

    val startAngleRad = angleNodeToLoopCenter - angleOffsetToTouchPoint + PI.toFloat()
    val endAngleRad = angleNodeToLoopCenter + angleOffsetToTouchPoint + PI.toFloat()

    val startAngleDeg = (startAngleRad * (180f / PI.toFloat()))
    var sweepAngleDeg = (endAngleRad - startAngleRad) * (180f / PI.toFloat())

    while (sweepAngleDeg < 0) sweepAngleDeg += 360f
    sweepAngleDeg %= 360f
    if (sweepAngleDeg == 0.0f && startAngleRad != endAngleRad) sweepAngleDeg = 360.0f

    drawScope.drawArc(
        color = color,
        startAngle = startAngleDeg.toFloat() - 90,
        sweepAngle = sweepAngleDeg.toFloat(),
        useCenter = false,
        topLeft = Offset(loopCenterX - loopRadius, loopCenterY - loopRadius),
        size = Size(loopRadius * 2, loopRadius * 2),
        style = Stroke(width = strokeWidth)
    )

    val arrowPosAngle = endAngleRad - 0.1f
    val arrowTipX = loopCenterX + loopRadius * cos(arrowPosAngle)
    val arrowTipY = loopCenterY + loopRadius * sin(arrowPosAngle)
    val arrowTangentAngle = arrowPosAngle + PI.toFloat() / 2f

    drawArrowHeadLoop(drawScope, Offset(arrowTipX, arrowTipY), arrowTangentAngle.toFloat(), color, 1f)
}

// Helper to draw arrowhead for self-loop
private fun drawArrowHeadLoop(drawScope: DrawScope, tipPos: Offset, tangentAngle: Float, color: Color, viewScale: Float) {
    // Use the default constant
    val headLength = DEFAULT_EDGE_ARROW_LENGTH / viewScale

    // Use the default constant
    val angle1 = tangentAngle + DEFAULT_EDGE_ARROW_ANGLE_RAD
    val angle2 = tangentAngle - DEFAULT_EDGE_ARROW_ANGLE_RAD

    val arrowX1 = tipPos.x - headLength * cos(angle1)
    val arrowY1 = tipPos.y - headLength * sin(angle1)
    val arrowX2 = tipPos.x - headLength * cos(angle2)
    val arrowY2 = tipPos.y - headLength * sin(angle2)

    val path = Path().apply {
        moveTo(tipPos.x, tipPos.y)
        lineTo(arrowX1, arrowY1)
        lineTo(arrowX2, arrowY2)
        close()
    }
    drawScope.drawPath(path, color = color)
}

// Calculate label position for self-loop
fun calculateSelfLoopLabelPosition(nodePos: Offset, nodeRadius: Float): Offset {
    val loopRadius = SELF_LOOP_RADIUS
    val controlOffsetAngle = SELF_LOOP_OFFSET_ANGLE

    val centerAngle = controlOffsetAngle - (PI / 2f).toFloat()
    val centerDist = nodeRadius + loopRadius
    val loopCenterX = nodePos.x + centerDist * cos(centerAngle)
    val loopCenterY = nodePos.y + centerDist * sin(centerAngle)

    val labelDist = loopRadius * 1.5f
    val labelAngle = centerAngle
    return Offset(
        loopCenterX + labelDist * cos(labelAngle),
        loopCenterY + labelDist * sin(labelAngle)
    )
}

// Text Drawing using TextMeasurer
@OptIn(ExperimentalTextApi::class)
fun drawLabelCompose(
    drawScope: DrawScope,
    textMeasurer: TextMeasurer,
    text: String?,
    position: Offset,
    color: Color = Color.Black,
    fontSize: TextUnit = 12.sp,
    maxTextWidth: Float = Float.POSITIVE_INFINITY,
    onMeasured: (Float, Float) -> Unit = { _, _ -> }
) {
    if (text.isNullOrBlank()) return

    val textStyle = TextStyle(
        color = color,
        fontSize = fontSize,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )

    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(text),
        style = textStyle,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxTextWidth.roundToInt())
    )

    val textSize = textLayoutResult.size
    val topLeft = Offset(
        position.x - textSize.width / 2f,
        position.y - textSize.height / 2f
    )

    drawScope.drawText(
        textLayoutResult = textLayoutResult,
        topLeft = topLeft
    )

    onMeasured(textSize.width.toFloat(), textSize.height.toFloat())
}
