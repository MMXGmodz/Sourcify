package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandableBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    containerBrush: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.fillMaxHeight(),
        sheetState = sheetState,
        containerColor = if (containerBrush != null) Color.Transparent else containerColor,
        contentColor = contentColor,
        dragHandle = if (containerBrush == null) ({ BottomSheetDefaults.DragHandle() }) else null,
    ) {
        if (containerBrush == null) content()
        else Column(Modifier.fillMaxSize().background(containerBrush, BottomSheetDefaults.ExpandedShape)) {
            Box(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                if (sheetState.currentValue == SheetValue.PartiallyExpanded) expand {
                    scope.launch { sheetState.expand() }; true
                }
                if (sheetState.currentValue == SheetValue.Expanded && sheetState.hasPartiallyExpandedState) collapse {
                    scope.launch { sheetState.partialExpand() }; true
                }
                dismiss {
                    scope.launch { sheetState.hide(); onDismissRequest() }; true
                }
            }, contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
            content()
        }
    }
}
