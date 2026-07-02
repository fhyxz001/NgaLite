# 底部导航栏 + 社区首页重构计划

## 背景与目标

当前应用启动后直接进入帖子列表页（`LIST`），通过 `TopAppBar` 下拉菜单切换板块，设置页中另有「板块管理」入口。用户希望改成主流社区 App 的底部导航结构：

- 底部两个一级入口：**社区**、**设置**。
- **社区**页参考图 1：左侧分类列表 + 右侧板块网格，支持最近访问、我的收藏、收藏管理（星标）。
- **设置**页保留原有功能，但移除「板块管理」。
- 点击板块进入帖子列表（参考图 2），该页为子页面，底部导航隐藏。

## 约束

- 仅 `晴风村`（fid `-7955747`）与 `网事杂谈`（fid `-7`）需要登录。
- 所有导航操作必须通过 `navSafe` 包装（500 ms 防抖 + `runCatching`）。
- 帖子详情路由保持 `detail/{tid}`，不传递 `forumName`。
- 所有列表项必须有唯一 key。
- 启动后默认展示用户收藏板块（新社区页中通过默认选中「包含首条收藏的分类」/置顶「我的收藏」满足）。
- UI 图标使用 Coil `AsyncImage` 异步加载 `assets/icons/f{fid}.png`。

## 推荐方案：扁平路由 + 条件底部导航

### 1. 新路由定义

```kotlin
object Routes {
    const val COMMUNITY     = "community"
    const val SETTINGS      = "settings"
    const val LOGIN_WEB     = "login_web"
    const val FORUM_THREADS = "forum_threads/{fid}"
    const val DETAIL        = "detail/{tid}"

    fun forumThreads(fid: String) = "forum_threads/$fid"
    fun detail(tid: String)      = "detail/$tid"
}
```

- 移除原 `LIST` 与 `FORUM_SELECT`。
- `NavHost` 外层用 `Scaffold` 包裹，根据当前路由是否属于一级页来决定是否显示 `NavigationBar`。

### 2. 页面结构

```
NavHost(startDestination = Routes.COMMUNITY)
├── community        -> CommunityScreen      （底部导航显示）
├── settings         -> SettingsScreen       （底部导航显示）
├── forum_threads/{fid} -> ForumThreadsScreen （底部导航隐藏）
├── detail/{tid}     -> DetailScreen         （底部导航隐藏）
└── login_web        -> LoginWebScreen       （底部导航隐藏）
```

### 3. 新增/改造文件

#### 新增

1. **`app/src/main/java/com/ngalite/app/data/RecentVisitStore.kt`**
   - 与 `FavoriteStore` 风格一致，使用 `SharedPreferences`。
   - 接口：`getRecent(): List<String>`、`record(fid: String)`（去重、插到头部、最多 20 条）。

2. **`app/src/main/java/com/ngalite/app/ui/CommunityScreen.kt`**
   - 社区首页 UI：
     - `TopAppBar` 标题「社区」。
     - 顶部「最近访问」`LazyRow`。
     - 顶部「我的收藏」`LazyRow`（收藏非空时）。
     - 主体 `Row`：左侧分类 `LazyColumn`（固定宽度约 100 dp），右侧板块 `LazyVerticalGrid`（`GridCells.Adaptive(84.dp)`）。
   - 网格项：图标 + 名称 + 右上角星标；点击非星标区域进入板块；点击星标切换收藏。
   - 分类项/网格项/最近访问项使用唯一 key：`cat_...`、`forum_...`、`recent_...`。

3. **`app/src/main/java/com/ngalite/app/ui/CommunityViewModel.kt`**
   - 加载 `ForumRepository.categories`。
   - 维护 `selectedCategory`、`favoriteFids`、`recentFids`。
   - 提供 `toggleFavorite(fid)`、`recordVisit(fid)`。
   - 默认选中包含第一个收藏板块的分类。

4. **`app/src/main/java/com/ngalite/app/ui/ForumIcon.kt`**（可选但建议）
   - 复用图标组件：优先 Coil 加载 `assets/icons/f{fid}.png`，兜底显示首字母。

#### 修改

5. **`app/src/main/java/com/ngalite/app/ui/NavGraph.kt`**
   - 更新 `Routes`。
   - 用外层 `Scaffold` + `NavigationBar` 包裹 `NavHost`。
   - `startDestination` 改为 `Routes.COMMUNITY`。
   - 重写 `ForumThreads` 与 `Detail` composable：`Detail` 通过 `nav.getBackStackEntry(Routes.FORUM_THREADS)` 取 `ListViewModel` 拿到 `currentForum.name`。

6. **`app/src/main/java/com/ngalite/app/ui/ListScreen.kt`**
   - 重命名为 `ForumThreadsScreen`（同步改文件名可选）。
   - 移除板块下拉切换、设置入口、板块管理入口。
   - 新增参数 `fid: String`、`onBack: () -> Unit`、`onTopicClick: (String) -> Unit`。
   - `TopAppBar` 改为：返回键 + 板块图标/名称 + 动作按钮。
   - `ListViewModel` 不再在 `init` 中自动加载；通过 `LaunchedEffect(fid)` 调用 `vm.loadForum(fid)`。
   - 异常捕获统一改为 `catch (t: Throwable)`，仅重新抛出 `CancellationException`。

7. **`app/src/main/java/com/ngalite/app/ui/SettingsScreen.kt`**
   - 删除 `onForumManageClick` 参数与「板块管理」Card。
   - `onBack` 改为可空；底部导航中的 Settings tab 传 `null`，隐藏返回图标。

#### 删除

8. **`app/src/main/java/com/ngalite/app/ui/ForumSelectScreen.kt`**
   - 功能完全由 `CommunityScreen` 替代，确认无引用后删除。

### 4. 数据与导航时序

```
启动
 └─> NavHost start = COMMUNITY
     └─> CommunityScreen
         └─> 加载 categories，默认选中含首条收藏的分类

点击板块
 └─> CommunityScreen: recordVisit(fid) + navSafe { nav.navigate("forum_threads/$fid") }
     └─> ForumThreadsScreen
         └─> LaunchedEffect(fid) { vm.loadForum(fid) }
             └─> 定位成功则 RecentVisitStore.record(fid)
         └─> 点击帖子: navSafe { nav.navigate("detail/$tid") }

帖子详情
 └─> DetailScreen
     └─> 通过 nav.getBackStackEntry("forum_threads/{fid}") 取 ListViewModel
     └─> 获取 currentForum.name 显示
```

## 验证步骤

1. 构建：`./gradlew :app:assembleDebug` 无报错。
2. 启动后显示「社区」页，底部有「社区 / 设置」两项。
3. 社区页左侧分类可切换，右侧网格刷新；顶部出现「最近访问」与「我的收藏」。
4. 点击板块进入帖子列表，底部导航消失，TopAppBar 显示板块图标+名称+返回键。
5. 点击帖子进入 `detail/{tid}`，正确显示板块名；返回回到帖子列表。
6. 设置页无「板块管理」入口，其他功能正常。
7. 底部导航在社区/设置间反复切换状态正确，无快速点击异常。
8. 进入「晴风村 / 网事杂谈」未登录时弹出登录流程。
9. 所有 Lazy 列表无 key 重复崩溃。

## 关键文件

- `app/src/main/java/com/ngalite/app/ui/NavGraph.kt`
- `app/src/main/java/com/ngalite/app/ui/ListScreen.kt`
- `app/src/main/java/com/ngalite/app/ui/SettingsScreen.kt`
- `app/src/main/java/com/ngalite/app/ui/CommunityScreen.kt`（新增）
- `app/src/main/java/com/ngalite/app/ui/CommunityViewModel.kt`（新增）
- `app/src/main/java/com/ngalite/app/data/RecentVisitStore.kt`（新增）
