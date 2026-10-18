package kr.agv.cfgmanager.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.agv.cfgmanager.ui.theme.AgvSizes
import kr.agv.cfgmanager.ui.theme.AgvSpacing
import kr.agv.cfgmanager.ui.theme.AgvTheme
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.HorizontalDivider as M3HorizontalDivider
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField
import androidx.compose.material3.Switch as M3Switch

// iOS(HIG) 모양의 컨트롤. Material3 컴포넌트를 감싸 모양 · 색만 바꾼다 (동작은 그대로).
// 화면은 androidx.compose.material3 의 같은 이름 대신 이 파일의 것을 import 한다.

private val ButtonShape: Shape = RoundedCornerShape(AgvSizes.ButtonRadius)

/** 채운 버튼: 강조색 바탕 · 12dp 모서리 · 그림자 없음. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    M3Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        content = content,
    )
}

/** 보조 버튼: iOS 의 tinted 버튼. 테두리 대신 강조색 12% 바탕에 강조색 글자. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val accent = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    M3Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.12f),
            contentColor = accent,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        content = content,
    )
}

/** iOS 스위치: 켜짐 초록 트랙 · 흰 손잡이. */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val status = AgvTheme.status
    val colors: SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = status.switchOn,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = Color.White,
        uncheckedTrackColor = status.switchOff,
        uncheckedBorderColor = Color.Transparent,
        disabledCheckedThumbColor = Color.White,
        disabledCheckedTrackColor = status.switchOn.copy(alpha = 0.4f),
        disabledCheckedBorderColor = Color.Transparent,
        disabledUncheckedThumbColor = Color.White,
        disabledUncheckedTrackColor = status.switchOff.copy(alpha = 0.5f),
        disabledUncheckedBorderColor = Color.Transparent,
    )
    M3Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled, colors = colors)
}

/** 입력칸: 셀 색 바탕 · 10dp 모서리 · 가는 테두리, 포커스 때 강조색 테두리. */
@Composable
fun OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
) {
    val cell = MaterialTheme.colorScheme.surfaceContainer
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = cell,
        unfocusedContainerColor = cell,
        disabledContainerColor = cell,
        errorContainerColor = cell,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
    M3OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.small,
        colors = colors,
    )
}

/** 머리카락 굵기 구분선 (0.5dp, separator 색). */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = AgvSizes.Hairline,
    color: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    M3HorizontalDivider(modifier = modifier, thickness = thickness, color = color)
}

/** 그룹 안 행 사이의 들여쓴 구분선 (왼쪽 16dp 들여쓰기). */
@Composable
fun InsetDivider(modifier: Modifier = Modifier, inset: Dp = AgvSizes.SeparatorInset) {
    HorizontalDivider(modifier = modifier.padding(start = inset))
}

/** inset grouped 컨테이너: 셀 색 바탕 · 10dp 모서리. 행 사이에는 [InsetDivider] 를 둔다. */
@Composable
fun InsetGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AgvSizes.GroupRadius),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(content = content)
    }
}

/** 상태 배지: 상태색 15% 바탕의 캡슐 + 같은 색 글자. 글자를 항상 함께 쓴다. */
@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(PaddingValues(horizontal = AgvSpacing.M, vertical = AgvSpacing.Xxs)),
        )
    }
}

/** inset grouped 목록용: 셀 색 바탕 · 10dp 모서리로 잘라낸다. LazyColumn 에 쓴다. */
@Composable
fun Modifier.insetGroup(): Modifier = this
    .clip(RoundedCornerShape(AgvSizes.GroupRadius))
    .background(MaterialTheme.colorScheme.surfaceContainer)

/** 세그먼트 컨트롤: 회색 트랙 위에 선택 항목만 셀 색으로 올라온다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = scheme.surfaceContainer,
                    activeContentColor = scheme.onSurface,
                    activeBorderColor = scheme.outlineVariant,
                    inactiveContainerColor = scheme.surfaceVariant,
                    inactiveContentColor = scheme.onSurface,
                    inactiveBorderColor = Color.Transparent,
                ),
                modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}
