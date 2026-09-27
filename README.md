# Deskpet Mobile · Android 桌宠

通过悬浮窗在 Android 桌面显示角色，支持拖动互动、双击聊天、AI 主动搭话和语音合成。角色使用 WebM 视频素材，聊天和 TTS 使用用户配置的远程接口。

**运行要求：Android 7.1（API 25）及以上；需要授予“在其他应用上层显示”权限。**

## 功能

| 功能 | 说明 |
| --- | --- |
| 角色选择 | 内置佩丽卡、彩叶、明日奈、辉夜、白子，同一时间显示一个角色 |
| 桌面互动 | 拖动调整位置，双击输入消息；支持待机、说话、微笑和拖动动画 |
| AI 聊天 | 配置 OpenAI 兼容聊天接口、API Key、模型、系统提示词和生成参数 |
| 主动搭话 | 可调整频率或自定义最小、最大间隔 |
| 多轮上下文 | 按当前角色的主对话组织上下文，可单独关闭 |
| 对话历史 | 按角色查看记录、选择主对话、新建对话，支持删除整段对话或单轮问答 |
| 两种聊天窗口 | 跟随角色的紧凑弹窗、屏幕底部的聊天卡片，可在设置中切换 |
| 时间信息 | 可选发送设备当前日期、时间和时区给模型，默认关闭 |
| 思考模式 | 可选择思考请求协议；思考过程独立显示，支持默认折叠 |
| TTS | 回复完成后生成并播放语音；支持 MiMo 音色克隆与自定义 OpenAI 兼容 TTS |
| 分组设置 | 六个二级页面，统一过渡动画，保留页面滚动位置与设置草稿 |

## 开始使用

1. 安装 APK，打开应用，按首页提示授予悬浮窗权限。
2. 进入“设置 → 角色与外观”，选择角色并调整悬浮窗尺寸。
3. 在“AI 服务”填写 API 地址、密钥和模型；在“人设与对话”配置角色提示词及多轮上下文。
4. 点击底部“保存设置”，返回首页启动桌宠。
5. 拖动角色调整位置，双击角色输入消息；首页和聊天窗口均提供历史入口。

修改设置后需要点击“保存设置”。跨二级页面切换会保留草稿；离开设置时可以选择保存、继续编辑或放弃更改。

悬浮窗权限用于显示桌宠与回复气泡。系统或厂商结束后台服务后，需要重新启动桌宠；应用没有承诺开机自启或永久后台驻留。

## AI、思考模式与时间

聊天接口需兼容 Chat Completions。API 地址、密钥及模型由用户提供；开启思考模式时，服务商需要支持所选的 `reasoning_effort` 或 `enable_thinking` 参数。

最终回答与思考过程分开显示。思考过程可以折叠，不作为普通聊天正文或 TTS 输入；“开启思考模式”与“默认折叠思考过程”是两个独立选项。

“发送当前时间给模型”位于“人设与对话”，默认关闭。开启后，用户消息和主动搭话请求会附加设备现实时间及系统时区；时间信息只用于当次请求，不改写用户正文，不写入对话历史，也不替换角色扮演中的剧情时间。此选项不控制界面时间显示。

## 语音合成

入口：设置 → 语音合成 → AI 回复后生成并播放。

应用先等待完整的最终回复，再提交 TTS 请求，获取音频后播放。TTS 失败时保留文字回复；播放期间暂时静音角色视频。

### MiMo 音色克隆

默认配置：

| 配置项 | 值 |
| --- | --- |
| 服务 | MiMo |
| API 地址 | `https://api.xiaomimimo.com/v1` |
| 模型 | `mimo-v2.5-tts-voiceclone` |
| 鉴权 | 单独配置 TTS API Key |
| 参考音频 | 用户选择 MP3 或 WAV，大小不超过 7 MB |

当前应用上传参考音频进行克隆，不提供参考音频对应文字的输入项。音频复制到应用私有目录，调用时编码为 Data URL；更换参考音频需保存设置后生效。

接口说明：[MiMo-V2.5-TTS 文档](https://mimo.mi.com/docs/zh-CN/quick-start/usage-guide/audio/speech-synthesis-v2.5)。

### 自定义 TTS

选择“自定义 OpenAI 兼容 TTS”，配置 API 地址、独立密钥、模型及音色 ID。

当前实现调用 `POST {API 地址}/audio/speech`，请求包含 `model`、`input`、`voice`、`response_format: wav`，响应需为 WAV 音频字节。其它接口格式需要单独适配。

## 对话历史

每个角色分别保留一个主对话。选择“设为主对话”后，新消息和主动搭话写入该对话；查看其它角色历史不会更换桌面角色。

- 新建对话后，旧记录保留，新对话成为该角色的主对话。
- 关闭多轮上下文仍会保存历史，但请求不附带旧问答。
- 删除整段对话或单轮问答需要确认，删除后无法撤销。
- 历史保存在应用私有目录，关闭应用后仍可查看；卸载应用会移除本机数据。

详细规则见 [对话历史与主对话](docs/对话历史与主对话.md)。

## 本地构建

使用 JDK 17、Android SDK Platform 35、Gradle 8.7、Android Gradle Plugin 8.5.1。Gradle Wrapper 已包含在仓库中。

```powershell
git clone https://github.com/telecom888/deskpet-mobile.git
Set-Location deskpet-mobile
```

在项目根目录创建 `local.properties`，填写本机 SDK 路径，例如：

```properties
sdk.dir=D:/Android/Sdk
```

当前 Gradle 配置会在初始化时读取以下四项签名属性，包括执行 Debug 构建时。请在本机用户级 `~/.gradle/gradle.properties` 或 CI 中提供自己的配置；密钥路径可以使用绝对路径：

```properties
RELEASE_KEYSTORE_FILE=C:/path/to/your-release.jks
RELEASE_KEYSTORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

以上仅为占位示例，不是项目实际密码。发布自己的 APK 时应使用自己的签名证书。

PowerShell 中设置 JDK 17 后执行：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

Linux / macOS 使用 `./gradlew`。首次构建需要下载依赖；本机已有缓存时可添加 `--offline`。

产物位置：

- Debug：`app/build/outputs/apk/debug/app-debug.apk`
- Release：`app/build/outputs/apk/release/app-release.apk`

## 项目结构与文档

```text
app/src/main/java/       Kotlin 界面、桌宠服务、聊天与 TTS
app/src/main/assets/pets/  五个角色的 WebM 素材
app/src/test/            单元测试
docs/                    功能与配置说明
ASSETS.md                角色素材授权范围
LICENSE                  MIT 代码许可
```

- [功能与配置](docs/功能与配置.md)
- [设置页面排布](docs/设置页面规划.md)
- [聊天窗口与时间戳](docs/聊天窗口与时间戳.md)
- [对话历史与主对话](docs/对话历史与主对话.md)

## 开源协议与角色素材

源代码及项目文档采用 [MIT 协议](LICENSE)。

`app/src/main/assets/pets/` 下的角色 WebM **不属于 MIT 授权范围**。本仓库不授予对这些素材的额外使用、修改、再分发或商业使用许可；详细范围见 [角色素材授权说明](ASSETS.md)。
