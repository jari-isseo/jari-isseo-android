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
import com.jariisseo.android.net.FloorTarget
import com.jariisseo.android.net.Server
import com.jariisseo.android.ui.screen.FloorAddScreen
import com.jariisseo.android.ui.screen.EditScreen
import com.jariisseo.android.ui.screen.HomeScreen
import com.jariisseo.android.ui.screen.LoginScreen
import com.jariisseo.android.ui.screen.ShareScreen
import com.jariisseo.android.ui.screen.SignupScreen
import com.jariisseo.android.ui.screen.StoreScreen
import com.jariisseo.android.ui.screen.ViewScreen
import com.jariisseo.android.ui.theme.Bg
import kotlinx.coroutines.flow.first

/** #10: 화면 이름. #25: 작업 중인 층은 라우트 인자 대신 MainActivity가 들고 있음 (스캔 화면을 MainActivity가 그려서) */
object Route {
    const val LOGIN = "login"
    const val SIGNUP = "signup"   // #12
    const val HOME = "home"
    const val SCAN = "scan"
    const val EDIT = "edit"   // 탑뷰 보정
    const val VIEW = "view"   // 사장님 뷰
    const val STORE = "store"   // #42: 가게 정보
    const val SHARE = "share"   // #22: 손님에게 알리기 (링크·QR)
    const val FLOOR_ADD = "floor-add"   // #25: 새 층 이름 → 스캔
}

/** #16: ARCore 지원 여부. SUPPORTED일 때만 스캔 */
enum class ArSupport { CHECKING, SUPPORTED, UNSUPPORTED, UNKNOWN }

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
    target: FloorTarget?,
    onTargetChange: (FloorTarget) -> Unit,
    onSelectFloor: (target: FloorTarget, scan: Boolean) -> Unit,
    arSupport: ArSupport,        // #16: SUPPORTED가 아니면 홈에서 스캔 입구를 막고, 스캔 화면에 와도 홈으로
    onRecheckAr: () -> Unit,     // #16: 알 수 없음이면 다시 확인
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
                // #25: 층 보정 / 다시 스캔 / 새 층
                onEditFloor = { t ->
                    onSelectFloor(t, false)
                    nav.navigate(Route.EDIT) { launchSingleTop = true }
                },
                // 빠르게 두 번 눌러도 스캔이 쌓이지 않게 (두 스캔 화면이 같은 glView를 붙이면 크래시)
                onRescanFloor = { t ->
                    onSelectFloor(t, true)
                    nav.navigate(Route.SCAN) { launchSingleTop = true }
                },
                onAddFloor = { nav.navigate(Route.FLOOR_ADD) { launchSingleTop = true } },
                onStore = { nav.navigate(Route.STORE) { launchSingleTop = true } },
                onShare = { nav.navigate(Route.SHARE) { launchSingleTop = true } },
                arSupport = arSupport,
                onRecheckAr = onRecheckAr,
            )
        }
        composable(Route.FLOOR_ADD) {
            FloorAddScreen(
                onBack = { nav.popBackStack() },
                onStart = { name ->
                    onSelectFloor(FloorTarget.New(name), true)
                    // 스캔에서 X로 닫으면 홈으로 (층 추가 화면은 건너뜀)
                    nav.navigate(Route.SCAN) { popUpTo(Route.HOME); launchSingleTop = true }
                },
            )
        }
        composable(Route.STORE) {
            StoreScreen(onBack = { nav.popBackStack() }, onLoggedOut = { toLogin() })
        }
        composable(Route.SHARE) {
            ShareScreen(onBack = { nav.popBackStack() })
        }
        // #25: 액티비티가 다시 만들어지면(설정 변경·프로세스 종료) 작업 중인 층을 잃음 → 스캔·보정할 층이 없으니 홈으로
        //   (저장 안 한 스캔 결과는 원래도 이때 사라짐. 기존 층은 홈에서 다시 [보정])
        // #16: AR 지원이 확인되지 않았으면 홈으로 (층 추가 화면 등 다른 길로 와도 막힘)
        composable(Route.SCAN) {
            if (target == null || arSupport != ArSupport.SUPPORTED) {
                LaunchedEffect(Unit) { nav.popBackStack(Route.HOME, inclusive = false) }
                return@composable
            }
            scanContent({ nav.popBackStack() }, { nav.navigate(Route.EDIT) })
        }
        composable(Route.EDIT) {
            if (target == null) {
                LaunchedEffect(Unit) { nav.popBackStack(Route.HOME, inclusive = false) }
                return@composable
            }
            EditScreen(
                layout = layout,
                onChange = onLayoutChange,
                floorTarget = target,
                onTargetChange = onTargetChange,
                onBack = { nav.popBackStack() },
                onSaved = { nav.navigate(Route.VIEW) { popUpTo(Route.HOME) } },   // #15: 서버 저장 성공 후. 뒤로 가기 = 홈
            )
        }
        composable(Route.VIEW) {
            ViewScreen(onBack = { nav.popBackStack() })
        }
    }
}
