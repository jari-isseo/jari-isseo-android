package com.jariisseo.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jariisseo.android.net.Server
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
 * 스캔 화면은 AR 객체를 가진 MainActivity가 그려서 넘김 (onClose = 홈으로, onDone = [스캔 완료]).
 * #13: 보정할 배치(layout)는 MainActivity가 들고 있고, 보정 화면의 변경은 onLayoutChange로 돌려줌
 * #11: 사장님 API가 401(토큰 없음·만료)이면 어느 화면에 있든 로그인으로 (뒤로 가기 기록도 비움)
 */
@Composable
fun AppNav(
    scanContent: @Composable (onClose: () -> Unit, onDone: () -> Unit) -> Unit,
    layout: Layout?,
    onLayoutChange: (Layout) -> Unit,
) {
    val nav = rememberNavController()
    LaunchedEffect(nav) {
        Server.loggedOut.collect {
            nav.navigate(Route.LOGIN) {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
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
                // 빠르게 두 번 눌러도 스캔이 쌓이지 않게 (두 스캔 화면이 같은 glView를 붙이면 크래시)
                onScan = { nav.navigate(Route.SCAN) { launchSingleTop = true } },
            )
        }
        composable(Route.SCAN) {
            scanContent({ nav.popBackStack() }, { nav.navigate(Route.EDIT) })
        }
        composable(Route.EDIT) {
            EditScreen(
                layout = layout,
                onChange = onLayoutChange,
                onBack = { nav.popBackStack() },
                onSave = { nav.navigate(Route.VIEW) { popUpTo(Route.HOME) } },   // 저장 후 뒤로 가기 = 홈
            )
        }
        composable(Route.VIEW) {
            ViewScreen(onBack = { nav.popBackStack() })
        }
    }
}
