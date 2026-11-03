package com.sidefocus.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sidefocus.app.*
import kotlinx.coroutines.delay

@Composable
fun FocusApp(vm: FocusViewModel = viewModel()) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    var foreground by remember { mutableStateOf(true) }
    var finishDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { foreground = false; vm.background(true) }
            if (event == Lifecycle.Event.ON_START) { foreground = true; vm.background(false) }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.clearMessage() } }
    LaunchedEffect(vm.demo, vm.screen) {
        while (vm.demo && vm.screen == Screen.SESSION) { vm.demoHeartbeat(); delay(1000) }
    }
    BackHandler(vm.screen != Screen.HOME) {
        when (vm.screen) {
            Screen.SESSION -> finishDialog = true
            Screen.SETUP -> vm.cancelSetup()
            else -> vm.navigate(Screen.HOME)
        }
    }
    FocusEffects(vm, foreground)
    val showNavigation = vm.screen !in listOf(Screen.SETUP, Screen.SESSION)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showNavigation) NavigationBar {
                listOf(Triple(Screen.HOME, Icons.Outlined.Home, "홈"), Triple(Screen.STATS, Icons.Outlined.BarChart, "기록"), Triple(Screen.SETTINGS, Icons.Outlined.Tune, "설정")).forEach { (screen, icon, label) ->
                    NavigationBarItem(selected = vm.screen == screen, onClick = { vm.navigate(screen) }, icon = { Icon(icon, label) }, label = { Text(label) })
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (vm.screen) {
                Screen.HOME -> HomeScreen(vm, rows)
                Screen.SETUP -> SetupScreen(vm, foreground)
                Screen.SESSION -> SessionScreen(vm, foreground) { finishDialog = true }
                Screen.REPORT -> ReportScreen(vm)
                Screen.STATS -> StatisticsScreen(vm, rows)
                Screen.SETTINGS -> SettingsScreen(vm)
            }
        }
    }
    if (finishDialog) AlertDialog(onDismissRequest = { finishDialog = false }, title = { Text("이번 집중을 마칠까요?") },
        text = { Text("지금까지의 공부 시간과 자세를 기기에 저장하고 리포트를 보여 드릴게요.") },
        confirmButton = { TextButton(onClick = { finishDialog = false; vm.finish() }) { Text("저장하고 마치기") } },
        dismissButton = { TextButton(onClick = { finishDialog = false }) { Text("계속 집중하기") } })
}
