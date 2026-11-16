package com.jariisseo.android

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jariisseo.android.net.Server
import com.jariisseo.android.ui.screen.EditScreen
import com.jariisseo.android.ui.screen.HomeScreen
import com.jariisseo.android.ui.screen.LoginScreen
import com.jariisseo.android.ui.screen.ShareScreen
import com.jariisseo.android.ui.screen.SignupScreen
import com.jariisseo.android.ui.screen.StoreScreen
import com.jariisseo.android.ui.screen.ViewScreen
import com.jariisseo.android.ui.theme.Bg
import kotlinx.coroutines.flow.first

/** #10: 화면 이름 (층 단위 라우트는 #25) */
object Route {
    const val LOGIN = "login"
    const val SIGNUP = "signup"   // #12
    const val HOME = "home"
    const val SCAN = "scan"
    const val EDIT = "edit"   // 탑뷰 보정
    const val VIEW = "view"   // 사장님 뷰
    const val STORE = "store"   // #42: 가게 정보
    const val SHARE = "share"   // #22: 손님에게 알리기 (링크·QR)
}

/**
 * #10: 로그인 → 홈 → 스캔 → 보정 → 뷰.
 * 스캔 화면은 AR 객체를 가진 MainActivity가 그려서 넘김 (onClose = 홈으로, onDone = [스캔 완료]).
 * #13: 보정할 배치(layout)는 MainActivity가 들고 있고, 보정 화면의 변경은 onLayoutChange로 돌려줌
 * #11: 사장님 API가 401(토큰 없음·만료)이면 어느 화면에 있든 로그인으로 (뒤로 가기 기록도 비움)
 * #12: 자동 로그인 = 저장된 토큰이 있으면 홈에서 시작. 만료된 토큰이면 첫 사장님 API의 401에서 로그인으로 감
 */
@Composable
fun AppNav(
    scanContent: @Composable (onClose: () -> Unit, onDone: () -> Unit) -> Unit,
    layout: Layout?,
    onLayoutChange: (Layout) -> Unit,
    onSessionEnd: () -> Unit,
) {
    val nav = rememberNavController()
    // 로그인으로 (뒤로 가기 기록을 모두 비움): 401 또는 #42 로그아웃.
    // 이전 계정의 스캔·보정 배치를 지움 → 다른 계정으로 로그인해 저장해도 덮어쓰지 않음
    fun toLogin() {
        onSessionEnd()
        nav.navigate(Route.LOGIN) {
            popUpTo(nav.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
    LaunchedEffect(nav) {
        Server.loggedOut.collect { toLogin() }
    }
    // 시작 화면은 처음 한 번만 정함 (토큰을 읽는 잠깐은 배경만). 이후 토큰이 바뀌어도 NavHost를 다시 만들지 않음
    var start by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (start == null) start = if (Server.tokens.tokenFlow.first() != null) Route.HOME else Route.LOGIN
    }
    val startRoute = start
    if (startRoute == null) {
        Box(Modifier.fillMaxSize().background(Bg))
        return
    }

    NavHost(navController = nav, startDestination = startRoute) {
        composable(Route.LOGIN) {
            LoginScreen(
                onLoggedIn = {
                    nav.navigate(Route.HOME) { popUpTo(Route.LOGIN) { inclusive = true } }   // 뒤로 가기로 로그인에 안 돌아감
                },
                onSignup = { nav.navigate(Route.SIGNUP) { launchSingleTop = true } },
            )
        }
        // #12: 가입은 옆에서 밀려 들어오는 별도 화면. 가입 성공 = 로그인·가입 화면을 모두 지우고 홈으로
        composable(
            Route.SIGNUP,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End) },
        ) {
            SignupScreen(
                onSignedUp = { nav.navigate(Route.HOME) { popUpTo(Route.LOGIN) { inclusive = true } } },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Route.HOME) {
            HomeScreen(
                onView = { nav.navigate(Route.VIEW) },
                onEdit = { nav.navigate(Route.EDIT) },
                // 빠르게 두 번 눌러도 스캔이 쌓이지 않게 (두 스캔 화면이 같은 glView를 붙이면 크래시)
                onScan = { nav.navigate(Route.SCAN) { launchSingleTop = true } },
                onStore = { nav.navigate(Route.STORE) { launchSingleTop = true } },
                onShare = { nav.navigate(Route.SHARE) { launchSingleTop = true } },
            )
        }
        composable(Route.STORE) {
            StoreScreen(onBack = { nav.popBackStack() }, onLoggedOut = { toLogin() })
        }
        composable(Route.SHARE) {
            ShareScreen(onBack = { nav.popBackStack() })
        }
        composable(Route.SCAN) {
            scanContent({ nav.popBackStack() }, { nav.navigate(Route.EDIT) })
        }
        composable(Route.EDIT) {
            EditScreen(
                layout = layout,
                onChange = onLayoutChange,
                onBack = { nav.popBackStack() },
                onSaved = { nav.navigate(Route.VIEW) { popUpTo(Route.HOME) } },   // #15: 서버 저장 성공 후. 뒤로 가기 = 홈
            )
        }
        composable(Route.VIEW) {
            ViewScreen(onBack = { nav.popBackStack() })
        }
    }
}
