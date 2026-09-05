# AL 远程网页壳（Capacitor + Android）

一个用 **Capacitor 8** 制作的极简"远程壳"Android 项目：

- App 启动后 **直接加载远程网页** `https://al.mingjia00.com.cc`，**不内置、不展示任何本地网页**；
- 内置 **GitHub Actions** 工作流，上传到 GitHub 仓库后自动编译 APK（Debug + Release）；
- **原生处理安卓返回键**：网页内有历史记录时按返回键 → 网页内回退（不退出 App）；已停在站点首页时 → 提示"再按一次返回键退出应用"，2.5 秒内再按一次才退出。

---

## 一、项目结构

```
al-remote-shell/
├── capacitor.config.json          # 核心配置：appId、应用名、远程网址 server.url
├── package.json                   # npm 依赖（@capacitor/core|cli|android）
├── package-lock.json
├── .gitignore
├── www/index.html                 # 构建占位页（永不显示，仅满足构建工具）
├── android/                       # Android 原生工程（Capacitor 生成 + 已定制）
│   ├── app/src/main/java/com/mingjia00/al/MainActivity.java   # ★ 返回键处理
│   ├── app/build.gradle           # ★ 已加入 Release 签名（正式/调试兜底）
│   └── ...                        # Gradle Wrapper、AndroidManifest 等
└── .github/workflows/build-apk.yml  # ★ 自动编译 APK 的 GitHub Actions
```

带 ★ 的是本项目为你定制/新增的文件。

---

## 二、关键原理

1. **启动直接加载远程网页**
   `capacitor.config.json` 中配置了 `server.url`，App 启动后 WebView 直接打开该网址，
   完全不会加载 `www/` 下的任何页面（`www` 目录只是 Capacitor 构建要求的占位）。
   因此网址后续改版、更新，**无需重新发包**。

2. **返回键 = 网页内回退（原生实现）**
   `MainActivity.java` 用 AndroidX `OnBackPressedDispatcher` 拦截返回键，
   不依赖网页端任何 JS，网页内跳转 / 刷新之后依然有效：
   - WebView 可回退（`canGoBack()`）→ 执行 `goBack()`，留在 App 内；
   - 已到站点首页 → Toast 提示，2.5 秒内再按一次才 `finishAffinity()` 退出。

---

## 三、上传到 GitHub，自动编译 APK

### 第 1 步：新建 GitHub 仓库（可设为 Private）

在 GitHub 网页上：`New repository` → 填仓库名（如 `al-remote-shell`）→ 创建。
**不要**勾选 "Add a README / .gitignore"，保持空仓库即可。

### 第 2 步：把本文件夹内容推送到仓库

在本机打开终端（Git Bash / CMD），进入项目目录后执行：

```bash
cd al-remote-shell

git init
git add -A
git commit -m "init: Capacitor remote shell for https://al.mingjia00.com.cc"

git branch -M main
git remote add origin https://github.com/<你的用户名>/al-remote-shell.git
git push -u origin main
```

> 第一次 push 会弹出 GitHub 登录窗口，按提示授权即可。
> `node_modules`、`android/build` 等已被 `.gitignore` 排除，不会上传。

### 第 3 步：等待自动编译

push 完成后，打开仓库页 → **Actions** 标签，能看到 `Build Android APK` 正在运行。
首次运行约 8~15 分钟（下载依赖 + Android SDK），之后缓存命中会快很多。

### 第 4 步：下载 APK

运行成功后，进入该次运行页面，最下方 **Artifacts** 区域有两个压缩包：

| Artifact | 内容 | 用途 |
|---|---|---|
| `APK-Debug` | `app-debug.apk` | 直接安装测试 |
| `APK-Release` | `app-release.apk` | 未配置正式签名时用 debug 证书兜底，可安装 |

下载解压后把 APK 传到手机安装即可。**Debug / Release 都可安装**。

> 注意：无论 Debug 还是"兜底 Release"，都使用 debug 证书签名，
> 只适合测试分发。**上架商店 / 正式对外发布前**，请按第五节配置正式签名。

---

## 四、改配置（最常用）

打开 `capacitor.config.json`：

```json
{
  "appId": "com.mingjia00.al",        // 包名，唯一标识。若上架需改成你自己的（见下文注意）
  "appName": "AL",                     // 桌面显示的应用名
  "webDir": "www",
  "server": {
    "url": "https://al.mingjia00.com.cc",  // ★ 换成你的网址即可
    "cleartext": false,
    "allowNavigation": ["al.mingjia00.com.cc"]
  }
}
```

- **只换网址**：改 `server.url`，并同步改 `allowNavigation` 里的域名。
- **改应用名**：改 `appName`，以及 `android/app/src/main/res/values/strings.xml` 里的 `app_name`。
- **改包名（appId）**：注意要同步改 4 处——
  1. `capacitor.config.json` 的 `appId`
  2. `android/app/build.gradle` 的 `namespace` 与 `applicationId`
  3. Java 源码目录路径 `android/app/src/main/java/com/mingjia00/al/`
  4. `android/app/src/main/res/values/strings.xml` 中 `package_name`、`custom_url_scheme`

  改完最好重新 `npx cap sync android`。**建议：能不改包名就别改，改动最容易出错。**

> `android/app/src/main/assets/capacitor.config.json` 是编译时拷贝的副本，
> 由 `npx cap sync android` 自动刷新；CI 里每次构建都会执行 sync，不必手动管。

---

## 五、（可选）Release 正式签名

需要上架商店 / 正式分发时，生成正式 keystore 并配置到仓库 Secrets：

**1. 生成 keystore（本机，JDK 环境）**

```bash
keytool -genkeypair -v -keystore release.keystore -alias my-alias \
  -keyalg RSA -keysize 2048 -validity 10000 -storepass 你的密码
```

**2. 转成 Base64 并记住三个值**

Windows PowerShell 里执行：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore")) | Out-File -Encoding ascii keystore.b64
```

打开 `keystore.b64`，整段内容即 `KEYSTORE_BASE64`。

**3. 在仓库配置 4 个 Secrets**

GitHub 仓库 → `Settings` → `Secrets and variables` → `Actions` → `New repository secret`：

| Secret 名 | 值 |
|---|---|
| `KEYSTORE_BASE64` | keystore.b64 里的整段 Base64 文本 |
| `KEYSTORE_PASSWORD` | keystore 密码（storepass） |
| `KEY_ALIAS` | 别名（如 my-alias） |
| `KEY_PASSWORD` | 密钥密码（keypass，通常同 storepass） |

配置后再次 push / 手动运行 Actions，产出的 `app-release.apk` 即正式签名。
**Secrets 不要泄露、不要提交到仓库**（`.gitignore` 已排除 `*.jks`、`keystore.properties`）。

---

## 六、返回键行为说明（已原生实现，无需改网页）

| 场景 | 返回键行为 |
|---|---|
| 网页内有多层页面/路由 | WebView 回退到上一页，App 不退出 |
| 已退到站点首页（无历史可退） | 提示"再按一次返回键退出应用" |
| 首页提示后 2.5 秒内再按一次 | 退出 App |

想改成"任意情况都不退出，只退到后台"，把 `MainActivity.java` 中
`finishAffinity();` 换成 `moveTaskToBack(true);` 即可（真机直接按 Home 也会退后台）。

> 附加提醒：个别站点会检查 WebView UA 或禁止非浏览器访问，若目标网页打不开或提示
> "请使用浏览器访问"，通常是网站侧策略，与壳无关。

---

## 七、本地开发（可选）

不需要本地环境也能靠 GitHub Actions 出包。想在本地跑：

1. 安装 [Node.js](https://nodejs.org) 20+、[Android Studio](https://developer.android.com/studio)
   （含 JDK 21、Android SDK 36）；
2. 终端执行：

```bash
npm install
npx cap sync android
npx cap open android     # 用 Android Studio 打开，点 Run 即可装到手机/模拟器
```

---

## 八、常见问题

- **构建失败？** 先看 Actions 日志。若与 SDK 版本相关，确认 `android/variables.gradle`
  中的 `compileSdkVersion` 与工作流里安装的 `platforms;android-36` 一致。
- **改了网址没生效？** 确认改动的是仓库里的 `capacitor.config.json`（不是 android assets 副本），
  并重新 push，CI 会执行 sync。
- **想每次推送都出包？** 已是默认行为（push 到 main/master 即触发）；
  也可在 Actions 页手动 `Run workflow`。

版本信息：Capacitor 8.5.1 · AGP 8.13.0 · Gradle 8.14.3 · compileSdk 36 · minSdk 24（Android 7.0+）
