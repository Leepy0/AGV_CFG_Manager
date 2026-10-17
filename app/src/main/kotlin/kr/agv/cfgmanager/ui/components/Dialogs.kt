package kr.agv.cfgmanager.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import kr.agv.cfgmanager.ui.theme.AgvSizes

/**
 * 확인 대화상자. 버튼은 동사로 쓴다. 취소가 기본이고 Esc(뒤로 가기)도 취소다 (field-app-ui-design: 위험 동작).
 * [danger] 이면 확인 버튼을 알람 색으로 한다. 색만으로 뜻을 전하지 않도록 문구는 항상 함께 쓴다.
 * [cancelFocused] 이면 열릴 때 취소 버튼에 초점을 둔다 (되돌릴 수 없는 동작의 기본 선택은 취소).
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String = "취소",
    danger: Boolean = false,
    cancelFocused: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(cancelFocused) {
        if (cancelFocused) cancelFocus.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = AgvSizes.TouchMin),
            ) {
                Text(confirmLabel, color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = AgvSizes.TouchMin).focusRequester(cancelFocus),
            ) { Text(dismissLabel) }
        },
    )
}
