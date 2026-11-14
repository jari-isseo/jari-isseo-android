package com.jariisseo.android

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jariisseo.android.ui.screen.EditScreen
import com.jariisseo.android.ui.screen.HomeScreen
import com.jariisseo.android.ui.screen.LoginScreen
import com.jariisseo.android.ui.screen.ViewScreen

/** #10: 화면 이름 (층 단위 라우트는 #25) */
object Route {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SCAN = "scan"
    const val EDIT = "edit"   // 탑뷰 보정
    const val VIEW = "view"   // 사장님 뷰
}

/**
 * #10: 로그인 → 홈 → 스캔 → 보정 → 뷰.
 * 스캔 화면은 AR 객체를 가진 MainActivity가 그려서 넘김 (onClose = 홈으로, onDone = [스캔 완료])
 */
@Composable
fun AppNav(scanContent: @Composable (onClose: () -> Unit, onDone: () -> Unit) -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Route.LOGIN) {
        composable(Route.LOGIN) {
            LoginScreen(onLogin = {
                nav.navigate(Route.HOME) { popUpTo(Route.LOGIN) { inclusive = true } }   // 뒤로 가기로 로그인에 안 돌아감
            })
        }
        composable(Route.HOME) {
            HomeScreen(
                onView = { nav.navigate(Route.VIEW) },
                onEdit = { nav.navigate(Route.EDIT) },
                onScan = { nav.navigate(Route.SCAN) },
            )
        }
        composable(Route.SCAN) {
            scanContent({ nav.popBackStack() }, { nav.navigate(Route.EDIT) })
        }
        composable(Route.EDIT) {
            EditScreen(
                onBack = { nav.popBackStack() },
                onSave = { nav.navigate(Route.VIEW) { popUpTo(Route.HOME) } },   // 저장 후 뒤로 가기 = 홈
            )
        }
        composable(Route.VIEW) {
            ViewScreen(onBack = { nav.popBackStack() })
        }
    }
}
