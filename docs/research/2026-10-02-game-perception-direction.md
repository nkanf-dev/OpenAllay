# 游戏助手感知方向研究：结构化观测、玩家附图与未来单次游戏截图

- 研究日期：2026-10-02。
- 状态：方向研究，不是已批准的实现计划，也不是运行验收。
- 本次边界：只读仓库源码/文档和公开官方资料；不运行 Gradle、不启动游戏、不读取实际剪贴板、不调用模型推理、不读取凭据或环境变量、不改产品代码。
- 交付：本文归档于 `docs/research/2026-10-02-game-perception-direction.md`，仅作为方向研究；本文不授权实现游戏截图采集。
- 源码基线：开始读取时仓库 HEAD 为 `9a0e4232deb8db3b14908d2d17366bb5dbb16335`。同期其他工作可能增加用户图像输入；本文不把并行工作描述为已经验收的产品能力。
- 官方动态网页均于 2026-10-02 核验。没有明确发布日期的页面仅记访问日期，不推测发布日期。论文版本与发布/更新日期见第 11 节。

## 1. 结论

**优先让助手理解“玩家指的是什么”，而不是让模型持续看游戏。**

建议采用三类互补证据：

1. **结构化游戏观测**：准确读取物品 ID、数量、选中槽位、瞄准方块、位置、当前同步菜单、公开设置/F3 数值。按任务取需要的部分。
2. **玩家主动提供的图片**：本次同期开拓的能力是手动粘贴图片作为消息附件。适合建筑外观、UI 布局、资源包/光影差异、陌生模组画面、攻略/参考图。它不是游戏自动取图。
3. **未来手动单次游戏截图**：只有在用户明确选择“附上当前游戏画面”时，抓一帧、预览、选择发送。复用 Minecraft 原生截图/渲染抽象，不后台轮询、不按每个对话轮次自动截图、不维护持续画面历史。**这次明确不实现。**

像素图帮助理解外观和界面布局；结构化数据帮助确认具体对象和精确数值。二者不能互相替代。模型收到了某张图，才具备对那张图的视觉输入。系统知道坐标或实体列表，不等于模型已经看过当前画面；一张图片更不等于“看到了完整世界”。

这是面向玩家的游戏助手，不是企业内容审查系统。隐私设计以明确来源、可见附件、手动发送、可移除、有限保留为主。不要借此加入 DLP、全局凭据扫描、OCR 审查、正则内容封锁或每次发送审批。

## 2. 现有仓库能证明什么

下面的“已有”来自实现，而不是旧设计文档。行号针对上述基线。

| 现有表面 | 实现证据 | 能提供的事实与边界 |
| --- | --- | --- |
| 玩家/背包 | `common/src/main/java/dev/openallay/client/context/ClientContextCapture.java:89,447`，`context/PlayerSnapshot.java`，`context/InventorySnapshot.java` | 客户端线程采样，脱离游戏对象后形成记录。维度、方块位置、模式、玩家背包槽、选中热键槽、副手。`ItemStackSnapshot` 当前只给 ID/数量/展示名，不是任意 NBT 或全部数据组件。 |
| HUD/F3 风格诊断 | `ClientContextCapture.java:304` | 坐标、方向、yaw/pitch、biome、FPS、帧耗时、渲染距离、生命/饥饿/状态效果、相机模式；准星目标类型、目标方块 ID/坐标或实体类型。这里是代码读取数值，不是 F3 截图，也不是所有调试覆盖层都被完整读取。 |
| UI/屏幕状态 | `ClientContextCapture.java:379`，`context/game/ObservableGameStateSnapshot.java` | 当前区分 `gameplay_or_player_inventory` 与 `open_synchronized_menu`，可给菜单类型。无法据此精确认定每个当前 Screen、悬停控件、工具提示、所有模组设置屏或容器内容。代码明确给 partial 和 `screen_identity_not_public`。 |
| 设置/资源包/模组 | `ClientContextCapture.java` 的 options/packs/mods/shader section | 原版公开设置、按键、可用/选中资源包、loader 公开模组 metadata；shader 没有兼容公开 adapter 时为 unknown。服务端不具有玩家客户端的完整设置/屏幕/画面。 |
| 有界世界区域 | `world/WorldObservationRequest.java`，`world/MinecraftClientWorldObservationCoordinator.java:68,147,251`，`world/WorldObservationCoverage.java` | 明确 bounds 后读取已加载方块和客户端实体。未加载 chunk 或超出建造高度记 unavailable；不会把缺失区块当 air。每片最多处理 2,048 个位置后让出客户端线程。它不是当前镜头的可见面提取，也不是世界完整扫描。 |
| 证据来源 | `context/EvidenceMetadata.java`，`context/DataAuthority.java`，`world/WorldObservationEvidence.java` | 已有 authority、completeness、capturedAt、sourceId、provenance、游戏/loader 信息。客户端观测与服务端权威分别表达。数据可用性不等于画面可见性。 |
| 原生截图用于开发验证 | `guide/e2e/GuideClientE2EController.java:909` | 已有 E2E `Screenshot.grab(..., client.gameRenderer.mainRenderTarget(), ...)` 调用。它保存验证 PNG。不是面向玩家的模型图像输入，不能据此宣称助手已能看图。 |
| 模型输入基线 | `model/ModelContent.java:7`，`model/openai/OpenAiJsonCodec.java`，`model/anthropic/AnthropicJsonCodec.java`，`model/ProviderModelClients.java:13` | 基线只存在 Text、Reasoning、ToolUse、ToolResult。生产适配器是 OpenAI Chat Completions 和 Anthropic Messages。图像需要真正编码为 provider 原生 image part；文本里放文件名、base64 字符串或“截图已附上”不构成视觉输入。 |
| 能力与计数 | `model/metadata/ModelMetadata.java`，`model/catalog/ModelCatalog.java`，`docs/model-token-accounting.md` | 现有 metadata 主要是 context/output limit，provider `/models` catalog 只解析 model ID；不能从目录中的 vision 名字或大 context 推断能接图。离线文本 tokenizer 也不计算真实图像 token。 |

`docs/isme/decisions/2026-07-19-020-observable-game-state-and-request-visibility.md` 可用于理解原来的“玩家可观测外层状态 vs 独立空间能力”分工，但其中旧 schema/迁移与审查措辞不应覆盖后来的 Latest Only 和玩家优先决定。

## 3. 感知不是越多越好：优先显式指向

“这个”“我手里的”“这一格”“这里怎么摆”需要对象锚点。建议未来按玩家明确选择形成小型上下文附件，不自动倾倒所有数据。

| 玩家指向 | 优先结构化内容 | 图片的补充价值 | 不应悄悄做的事 |
| --- | --- | --- | --- |
| 我手里的东西 | 主手/副手、槽位、物品 ID/展示名/数量；任务需要时再读公开 tooltip/组件 | 看图标、纹理、模组视觉状态 | 由图标猜出 ID 当成确定值；默认展开全部背包/装备/数据组件 |
| 准星对着的方块/实体 | 命中种类、准确坐标/实体身份、距离/命中面等可获得字段；以实际 snapshot 为准 | 建筑外观、朝向呈现、遮挡、附近视觉关系 | 让“这个”自动扩张为扫描所有附近 chunk、墙后实体或容器 |
| 当前菜单里这一格 | 用户选中/悬停槽、物品、屏幕标题、菜单种类；原版或模组公开 adapter | 按钮/布局、缺 adapter 的自绘 UI、tooltip 外观 | 认为同步菜单类型就是完整屏幕身份；把整个 Screen 对象反射序列化 |
| 这座建筑/这个区域 | 玩家明确选取 bounding region；边界、坐标、维度、loaded coverage、必要方块属性 | 美观、比例、材质、光照、参考图匹配 | 从单张正面图保证背面/内部/地下结构已看过 |
| 附近危险/实体 | 用户声明的附近范围或已选区域中的客户端同步实体；明确数值事实 | 画面里是否遮挡、可见危险、实际镜头状况 | “已加载”当“在视野内”，或替用户进行隐含全世界探索 |
| 我现在的画面/UI出问题 | 当前 screen/相机模式/FOV/GUI scale/渲染设置等任务相关字段 | 黑屏、布局错位、资源包、光影、乱码等直接视觉证据 | 对画面作了描述却没有真正附图；所有错误都先开全量诊断 |

建议先做好几种可理解的用户操作：“带上手持物品”“带上准星目标”“带上选中物品/区域”。这些是指代与任务上下文，不是权限升级。已有可用工具仍可按任务查询，不需为每个字段增加确认框。

屏幕是客户端对象，菜单是客户端/服务端同步交互状态，二者不是同义词。[M3] 明确区分普通客户端 Screen 与 server-handled menu。未来可通过稳定公开 API 读取已知屏幕标题、可访问控件标签、当前槽位和任务相关 hover；自绘模组 UI 没有统一语义树时，图片是补充或公开 adapter 的替代证据，而不是万能读取接口。

相机信息也应区分玩家身体朝向与真正渲染相机。第三人称、旁观/自由镜头、模组相机、FOV、partial tick 插值都可能使二者不同。截图描述不能把玩家位置误称镜头位置。

## 4. 结构化数据与像素的分工

### 4.1 优先结构化的任务

- 判断“我够不够材料”：物品数量/ID、配方来源、已有容器/背包证据，比 OCR 数字可靠。
- 确定“准星这块是什么”：实际 registry ID/状态，比纹理识别可靠。
- 精确坐标、方块属性、实体健康、选中槽、按键设置：直接读公开字段，不让模型从 HUD 猜数值。
- 确认加载覆盖、服务器权限、数据来源：像素没有这些保证。

### 4.2 图片明显更有价值的任务

- 建筑风格、对称感、可见立面、空间层次、材质/灯光效果。
- 没有专用 adapter 的自绘 GUI、工具提示、图表或报错画面。
- 用户复制的攻略图、设计参考、外部图纸、对比前后外观。
- 某个数值“正确”但视觉布局仍有问题的诊断。

### 4.3 不作虚假承诺

- 一张截图是二维投影。它没有被遮挡内容、背面、完整深度、所有 chunk、隐藏菜单，也不证明未出现对象不存在。
- GUI/HUD/F3 覆盖层属于当帧可见像素。它们是否出现取决于截图时状态，不能“补画”成当时存在。
- 正确说法：“这张图中可见……”“当前客户端已加载的选定区域中……”。错误说法：“我已经看过整个世界”“附近绝对没有……”“这就是你现在的完整屏幕”，尤其是只有旧粘贴图时。
- 模型可能误读文字、计数、细小物体和精确空间位置。将视觉推断和结构化确认分开，不把模型自己的描述升级为权威观测。

研究对照：UFO/UFO² 使用 GUI 图像与控件/UIA 信息结合，而不是仅靠像素猜控件；Cradle 特意以截图+键鼠作为无内建 API 的统一接口，证明像素路径可服务多类游戏，但它的通用性约束不是 Minecraft 内置助手要复制的要求。[R1–R3] 这些研究是架构参考，不是 OpenAllay 的性能或正确率验收，也不支持宣称 Minecraft 原生具有 Windows UIA 等语义树。

## 5. 本次同期的图片方向：用户手动粘贴附件

此节是方向和验收关注点，不表示本研究已实现或调用了剪贴板。

1. 用户主动粘贴图片或点击明确的“粘贴图片”。仅在此动作读取剪贴板。不要监听剪贴板变化、定时采集，或把普通文字粘贴当屏幕采集触发器。
2. 图片先成为 composer 中的可见附件。展示缩略图/来源/尺寸，允许移除。发送沿用正常发送操作，不必增加每图审批弹窗。
3. 附件与这条用户消息绑定。实际模型请求必须含 image content part；发送失败不能呈现为“模型已看过”。
4. 图片可能是旧截图、网页参考图、照片，甚至与 Minecraft 无关。只能记 `importedAt`。没有可信原始捕获信息时，不填伪造的游戏 capture time、维度、相机坐标或 server authority。
5. 以图为证的任务可以直接使用用户图。需要精确当前游戏事实时，再按任务查询结构化状态，并标明二者不一定同一时刻/场景。
6. 不支持图片的当前模型或网关应清楚提示。保留未发送附件供用户改模型/移除；不能静默丢图、偷偷换 provider、把 base64 当文本发送，或假装已经分析图片。
7. 消息数量、输入字节、解码后的像素总量、允许 MIME 类型、provider 原生限额是实际资源边界。限制用于性能和 API 正确性，不用于内容 DLP。数字在具体实现评估中决定，不把 provider 最大上限当产品推荐默认值。
8. 图片二进制不要写进系统提示、普通文本 token 估计、日志/Tool JSON 或每次历史预载。附件存储与可见消息关联；持久化、重启和删除行为要明确。历史若已不保留原图，就不能以说明文本假装恢复了视觉证据；用户主动删除之外，不应悄悄删除已承诺保留的附件。

成熟入口的事实：GLFW 的公开 clipboard API 处理 UTF-8 字符串，不提供通用图像剪贴板读取。[J1] Java 的 `DataFlavor.imageFlavor` 可表达图像传输，标准 `ImageIO` 可做编码/解码。[J2–J4] 可以评估已有桌面/loader/第三方能力，避免从零实现图像格式与平台剪贴板协议。Java API 存在不代表 AWT 在 Minecraft/macOS/Linux 当前运行环境下无条件可用；线程、平台和 headless 行为仍需后续实现验证。本研究没有实际读取剪贴板，也不选定新依赖。

## 6. 未来单次游戏截图：只规划，不实现

### 6.1 用户流程

建议先做单次、显式、容易理解的能力：

`玩家选择附上当前游戏画面 → 取得一帧 → composer 显示预览/来源 → 玩家正常发送`

- 明确截图目标是**游戏窗口**，不是整台电脑桌面。
- 明确“当前可见画面（含 HUD/UI）”与“仅游戏场景”是不同产品动作。如果聊天界面遮住场景，不能秘密关闭当前 UI 然后称之为原样截图。
- 游戏内热键先捕获场景，再打开聊天，可减少助手窗口遮挡；需明确 hotkey 的含义。另一方案是预览当前 UI 的图，默认保留真实可见状态。
- 可提供用户主动裁剪/局部选择。裁剪边界需可见；不在幕后做 OCR 隐私裁剪、内容判定或周期保存。
- 一次截图不授权持续捕获。禁止后台定时截图、每次模型调用自动附图、自动相机巡视、累积帧序列或截图历史缓存库。
- 图片包含聊天、玩家名、坐标、服务器/模组 UI 中的文本是可能发生的。使用前说明将发送到当前选定模型/provider，并让玩家预览/移除即可。不要转为 DLP 项目。

### 6.2 技术选择与线程边界

官方 Fabric 26.2 文档明确：26.2 有可选 Vulkan backend，**不支持直接依赖 raw OpenGL**；应通过 Blaze3D 抽象。[M1] 因此旧博客式 `glReadPixels` 截图教程不适合直接作为本项目实现依据。

复用首选为 Minecraft 原生 `Screenshot` 和主 render target，再用成熟编码接口处理脱离 GPU 的像素。仓库 E2E 已有原生调用；Fabric API 26.2 的测试源码也展示 `Screenshot.takeScreenshot(mainRenderTarget, callback)`。[M4] 这些证据说明“无需重造截图引擎”，不证明未来产品功能已经存在。

[M4] 的测试代码在 `computeOnClient` 中 update → extract → render → submit，再异步截图，测试线程等待其完成。**不要复制测试的强制重渲染、窗口 resize 或等待循环到正常聊天产品。** 产品应在正常帧生命周期选合适完成点，并在当时版本下核验原生 readback/callback 的资源所有权。

未来实现须遵守以下边界：

- 游戏/Screen/玩家/相机状态只在它们的所属客户端线程读取，并立即脱离成小型 immutable metadata。
- GPU/render target 访问遵守实际渲染/设备阶段，不从 Agent/model HTTP worker 直接触碰。不能假定“客户端线程上任意时刻”都代表完整渲染帧。
- Fabric 文档把 rendering 分为 extraction 与 drawing；会从 game state 提取 immutable render state，目标是上一帧绘制与下一帧提取并行。[M1–M2] 点击时 game tick state 与最终像素可能不是同一时刻。
- 在原生 readback 完成、像素脱离 GPU 后，resize/encode/hash/网络发送等耗时工作可移出游戏线程。真实 callback 所在线程、native image 关闭责任、readback copy/fence 和 resize 生命周期需阅读目标版本源实现确认；本次未证明这些细节。
- 不增加自定义 GPU backend、底层 OpenGL/Vulkan 层、桌面录屏服务或强制新的图像库。仅有明确功能缺口、跨平台证据和维护收益时再选成熟依赖。
- resize、minimize、关闭世界、切维度、取消请求、窗口重建、渲染故障都可能发生。失败应该保留用户文字/附件选择、给真实失败状态，释放图像资源；不静默补发另一帧。

NeoForge 的公开 feature rendering 文档当前标为 26.1，描述提交/渲染两阶段与提交后不可变对象。[M5] 只能作为同一渲染演进的旁证，不能将其当 26.2 截图 API 签名的直接证明。最终仍需按项目 Fabric 26.2/NeoForge 26.2 对应运行环境分别验收。

## 7. 多模态证据：来源、可见范围、时间与请求归属

### 7.1 权威不是一个总排序

| 证据 | 对什么有权威 | 不保证什么 |
| --- | --- | --- |
| 玩家手动附图 | 用户确实提交了这张图片 | 当前场景、真实捕获时间、服务器事实、游戏身份 |
| 游戏客户端原生截图 | 该捕获帧的可见像素与 UI 外观 | 墙后/未加载世界、精确实体身份、服务器状态、完整三维几何 |
| 客户端结构化观测 | 当时客户端同步/可访问的字段 | 所有数据都在相机视野内，或完整服务器知识 |
| 服务器结构化观测 | 获得授权并实际采样的服务端字段 | 玩家客户端 UI、资源包/光影、当时渲染像素 |
| 模型对图的描述 | 对所给证据的推断 | 独立观测或可执行动作的真实性 |

“更权威”必须按字段说。例如服务器可以确认方块状态，但不能反驳玩家截图上是否显示某个光影伪影；客户端截图可确认画面文字外观，但不能仅凭纹理推翻 registry ID。

### 7.2 最小必要元数据方向

未来单次截图可携带：来源类型、实际捕获时间、图像宽高/MIME/裁剪区域、是否含 HUD/UI、可验证的相机/屏幕信息、维度/当前世界连接归属，以及请求/会话的运行期关联。不要全量复制 diagnostics。具体内部 record 不在本文定型，也不加 schema/protocol version、版本文件名或迁移分支；运行期 generation/sequence 用于防迟到，**不是格式版本**。

用户粘贴图的最小事实是来源 `user-provided` 与导入时间。任何游戏位置/维度只能另列“发送时游戏观测”，不能与图片一起伪称捕获证据。

### 7.3 同步与迟到

- **时间戳不制造原子性。** 游戏状态采样、frame extraction、GPU drawing、readback 完成、图片编码、网络发送各有不同时间。至少区分 capture/requested time 与 completion/import time；仅“同 tick”也可能因相机插值/渲染延迟不等于同像素帧。
- 真正能把 structured state 与 frame 绑定时才称同一 capture。不能绑定时说明为 near-time，保留各自时间；跨机器 wall clock 也不应当成可靠排序依据。
- 现有方块区域观察跨多个 2,048-position slice，只有最终 evidence timestamp，不能声称区域在一个 tick 原子捕获。未来若与图结合，可记录开始/结束或分片范围，并注明 temporal consistency 边界。
- 取消、重试、切会话、换世界/角色/维度后，旧 readback/encode completion 不能自动附到新的消息或覆盖新的附件。按请求/会话/运行期 generation 检查归属；过期结果丢弃并释放资源。有效旧图仍可作为用户明确选中的“历史图”保留，但不可冒充 current。
- 真图迟到也仍是旧时刻的真图。它不能因到达得晚而覆盖更新的世界观测；反之，较新世界观测也不能把较早截图中的外观直接改成当前外观。

## 8. 空间路线：地图/选区/悬停坐标，而非隐含扫描

截图是当前视角的入口，不是地图能力的替代品。

建议未来将空间任务锚定到下列之一：玩家明确选择的地图范围、两个角点组成的 bounding region、准星/hover 方块坐标、选中实体，或用户明示的附近范围。助手解释它具体观察了哪一范围，返回维度、bounds 与 coverage。它不自行把“看看这里”升级为所有已加载 chunk 乃至全世界扫描。

关键边界：

- `hasChunkAt`/chunk loaded 仅说明当前请求能从客户端读到该区块，并不等于区块正在屏幕上、没有遮挡、最近一帧画过或状态来自服务端最终确认。
- 未加载与未知不是 air。图里没看见也不是世界里不存在。coverage partial 必须保留。
- 只读选择不隐含加载/生成 chunk，不自动移动相机/玩家，不打开墙后容器，不触发世界写入。
- 模组小地图/世界地图若存在稳定公开 API，可按用户选取范围接入；没有接口时用户附图仍可解释可见地图，不假装地图上的未知区块已经完整可查。
- 区域查询要按任务抽取/聚合必要事实并分配工作片，防止大 payload/帧卡顿。不要以任意“最多 N 个实体”偷偷截断并宣称完整；应给实际 coverage、可继续分页/细化的范围，以及性能预算的清晰状态。
- 最终职责是帮玩家完成任务。优先用用户选择避免“你是指哪里”的来回追问，但真正歧义时不能把猜的坐标/区域写成已选择。

## 9. Provider 图像输入、能力与成本事实

### 9.1 OpenAI：区分 Chat Completions 与 Responses

- **Chat Completions** 使用 `{"type":"image_url","image_url":{"url":"data:image/png;base64,..."}}` 或公网图片 URL；这是现有 OpenAllay OpenAI 协议要对接的 image part。[O1,O4]
- **Responses** 使用 `{"type":"input_image","image_url":"https://...或data:image/...;base64,..."}`，也支持 `file_id`。官方 Files 上传示例用 `purpose="vision"`。这不代表现有 Chat Completions adapter 接受相同形状。[O1]
- 能力必须按实际模型确定。官方 GPT-4.1 model page 列 input 为 text/image，output 为 text；GPT-3.5 Turbo 列 Image 不支持。不是所有 GPT，也不是所有 OpenAI-compatible gateway，都能收图。[O2,O3]
- 当前 images/vision guide 的分析输入要求为 PNG、JPEG、WebP、非动画 GIF；总 payload 每请求至多 **512 MB**、至多 **1,500 张**。这是当前公开图像分析 guide 的上限，不是产品默认值，也不证明用户 gateway、其他 OpenAI API、ChatGPT 上传或某个模型上下文能装下这些图片。patch 路径另有 resize/detail 与单图 patch 约束。[O1]
- token 计费随模型/尺寸/detail 变化。当前 guide 中 **gpt-4o/gpt-4.1** tile 路径：low 为 85 tokens；high/auto 依缩放后的 512px tile 数为 `85 + 170 × tiles`。**gpt-4o-mini** 的对应 base/tile 是 2,833/5,667，不能套用 85/170。patch 路径按 32px patch 与模型系数计算，detail/resize 预算又随模型变化。图像 tokens 按模型 input 定价，生成/编辑图像的 GPT Image 定价另算。[O1,O5]
- `detail` 的支持集与行为不是跨模型常量；当前 guide 也存在 low 比 high 使用更多 tokens 的模型路线。不能写成“low 永远便宜”“original 从不 resize”或“所有图片固定 N tokens”。[O1]
- 官方列明小字/非拉丁文字、旋转、精确空间定位、全景/鱼眼、计数和错误描述等局限；原始文件名/metadata 不作为视觉分析依据，resize 影响细节。准确 block/world 坐标仍需结构化观测。[O1]

核验限制：OpenAI 开发者源站在当前研究网络返回 403；同官方 guide/model page 内容通过公开 reader 读取，引用指向官方 URL。Chat Completions 形状另外从官方 `openai-openapi` 源码直接核验。reader 的 Published Time 不能当作者首次发布日期；上述数字是 2026-10-02 读取结果，不是 API 实测。[O1–O4]

### 9.2 Anthropic：base64 source 不是 OpenAI data URL

- **Messages** 使用 `{"type":"image","source":{"type":"base64","media_type":"image/png","data":"纯base64"}}`。URL 使用 `source:{"type":"url","url":"https://..."}`；当前直连 Claude 还支持 Files 的 `source.type="file"/file_id`。不是把 data URL 直接放在 Anthropic `data` 字段。[A1]
- 当前官方模型 overview 说其列出的 current models 支持 text/image input 与 text output。它不是对所有历史模型、兼容 gateway 或 partner 部署的保证。视觉理解也不等于原生图像生成/编辑。[A1,A2]
- 当前直连 API：200k context 模型每请求最多 **100 张**，其他模型 **600 张**；claude.ai 是每消息 20 张，不能混为 API 上限。单图最大 8000×8000；请求超过 20 图有更严格单图尺寸限制。历史重送和 tool_result 内图片都算图数。[A1]
- 单图上限为 **base64 编码后**直连 10 MB、Bedrock/Google Cloud 5 MB。标准 endpoint 总请求 32 MB，partner 可能更小；图数允许不等于 body 大小允许。当前 guide 说明 Bedrock/Google Cloud 仅支持 base64 source，不能把直连 URL/file_id 能力普遍外推。[A1]
- 当前 vision guide 使用处理后 `ceil(width/28) × ceil(height/28)` visual tokens。Claude 4.7 与后续 high-resolution tier 长边上限 2576px/4784 visual tokens；其他 standard tier 为 1568px/1568 tokens。一般超出 native resolution 的图片会缩小；computer/browser tool_result 的 screenshot/zoom 存在不同的拒绝规则，不能将普通用户附件的 resize 规则通用套入工具截图。[A1]
- 图像按所选模型 input token 价格计费。Files/file_id 可减少 payload 重传，不让视觉 token 免费。使用每图 `width×height/750` 或固定 1,600 tokens 的旧记忆作为当前通用公式是不可靠的。[A1,A3]
- 模糊、旋转、小图、计数和位置可能误判。后续若让模型标框，要尊重该 API 的输出坐标约定并处理实际 resize/crop；不要把视觉像素坐标直接当游戏坐标。[A1,A4]

这组字段/限额/公式已直接读取 Claude 官方页面；网页没有明确发布日期，记访问日期 2026-10-02。不根据模型营销名称自动生成能力规则，不调用 endpoint 试探。[A1–A4]

### 9.3 Gemini：仅作未来 provider 参考，不扩大本次生产适配范围

仓库基线没有 Gemini 原生协议。本节用于避免把所有 provider 图像输入当同一种格式，不建议这次新增 Gemini adapter。

- 当前 image guide 使用 **Interactions**：`type:"image"` 与纯 base64 `data`/`mime_type`，或 `uri`/`mime_type`，可以使用 Files API URI 与适用模型的公网 URL。它不是旧 `generateContent` 的字段形状。[G1,G3]
- guide 列 MIME 为 PNG/JPEG/WebP/HEIC/HEIF，每请求最多 3,600 image files；仍受 context、payload 与具体模型限制。[G1]
- **官方页面存在限额冲突**：image-understanding 的 inline 请求写 20 MB，file-input-methods 写 100 MB（PDF 50 MB）。两者未明确给出能消除差异的 API 划分。本研究不选一个数字冒充通用保证；未来可先采用较小资源界限，再按具体 endpoint 官方事实与实际验收确认。[G1,G3]
- 老图像路线常见 258 tokens 与 768px tile 描述；Gemini 3 专门的 media-resolution 页面则按 low/medium/high(default)/ultra_high 给约 280/560/1120/2240 tokens。不能称“Gemini 所有图都是 258”。按模型、media resolution 与实际 usage 核账；官方定价还会随模型、tier、时间和 Standard/Batch/Flex 改变。[G1,G2,G4]
- 当前模型页应单独核验输入/输出模态；例如 image input 支持不自动意味着 image generation 或 Live API 支持。图像定位的 0–1000 normalized bounding box 约定也不能普遍套到 Claude/OpenAI 或 Minecraft world coordinates。[G1,G2]

Google 上述指南显示 Last updated 2026-09-23 UTC，pricing 页显示 2026-10-01 UTC，均于 2026-10-02 读取。引用仅作 protocol/能力事实，没有调用 Gemini API。[G1–G4]

三家 computer-use 官方链路仍由应用提供截图/环境并执行模型请求，再回传结果；模型不会因支持截图推理而自行取得实时游戏画面或控制权。[C1–C3] 这是方向边界的补充证据，不表示本次要实现 computer-use agent。


### 9.4 对 OpenAllay 的含义

- 当前生产协议是 Chat Completions 与 Anthropic Messages。OpenAI Responses 的 `input_image` 示例可作为未来独立适配器参考，**不能把它的字段塞进现有 Chat Completions 请求**。
- model ID 列表、大 context、厂商名、`gpt-*`/`vision` 字样都不是图像能力证明。需要按具体 endpoint + 实际模型/deployment + API 协议的官方能力事实判断；自建/兼容网关还可能拒绝 image part 或实施更小限额。
- 视觉输入是模型推理输入，不是图像生成能力。能生成图也不自动证明能接用户图。
- 图片按实际处理后的视觉表示计 token/费用，不按 base64 的文本字符量计视觉 token。离线 JTokkit 的完整 JSON BPE 方法仍可估计文本/工具部分，但 image payload 必须分开处理并标“估计/未知”；不要把整段 base64 送给文本 tokenizer，也不报告它的字符数作为图像费用。
- provider 的 usage/账单与对应模型定价才是实际计费依据。图像尺寸/detail/resize、缓存政策、多轮重送会改变费用；同一附件二进制只存一次不代表多轮 provider 只收费一次。
- 当后续任务仍需原图，保留真实 image part；不再需要时可从未来上下文投影中省略/明确概述，不能假称概述等于原图。历史 UI 与模型当前上下文是否真的包含图，要分别表示。
- 本研究只读官方公开页面，没有调用 vision inference、token counting、Files upload 或 pricing API，也没有试探任何配置 endpoint。

## 10. 后续决策与验收关注点

以下是研究建议，不是本次执行清单。

### 当前同期开拓：手动图片附件

先确认真实 image part 到达选定模型，支持 composer 预览/移除，失败与不支持能力不静默降级，图片与消息/请求生命周期一致。覆盖正常文字粘贴、无图片、无可用图像类型、剪贴板被占用、损坏/超大图、多个附件、发送重试/取消、切会话与历史投影。跨 Windows/macOS/Linux 与实际游戏 JVM 单独验证，不只用单元测试宣称平台可用。

### 下一方向：显式对象上下文

优先选手持物品、准星目标、菜单选中槽位。验证对象 ID/坐标准确、未知 screen 真实 partial，像素外观与结构化值不相互冒充。无需先新增庞大的视觉 Agent 架构。

### 以后才考虑：单次游戏截图

用户明确批准后再写设计/实现计划。验收单帧目标、HUD/UI含义、第三人称/模组相机、帧/readback一致性、切维度/世界与取消迟到、窗口 resize/minimize、OpenGL/Vulkan 对应 backend、两 loader、线程资源释放、帧时间影响、附件缩放后小文字可读性。

### 继续后置：地图/空间选择

选择范围、loaded coverage、分片时间一致性、未知区块与空区块、无需全世界扫描、同一任务的结果大小与展示。不要把 screenshot 功能和世界扫描绑成一个实现大包。

系统 prompt 保持能力中立：依据实际提供的证据完成任务，缺失/过期证据就直接说明。Minecraft 类名、截图 API、特定模组适配方式、图像限额和视觉识别步骤应放实现、能力说明或按需 Skill，不写成硬编码 system task-if 分支。本研究不修改任何 prompt/Skill。

## 11. Primary evidence 与日期

### Minecraft / renderer：官方文档或官方实现

- **[M1] Fabric, Basic Rendering Concepts（26.2）**：<https://docs.fabricmc.net/develop/rendering/basic-concepts>；raw source <https://github.com/FabricMC/fabric-docs/blob/bee3ba94ae7d044a1e0b316f3eb5f8650a925138/develop/rendering/basic-concepts.md>。路径最新提交日期 2026-07-22，提交 `bee3ba94...`（Port to 26.2）；不是首次发布日期。访问 2026-10-02。支持 Blaze3D/OpenGL–Vulkan 边界与 extraction/drawing 演进事实。
- **[M2] Fabric, Rendering in the World（26.2）**：<https://docs.fabricmc.net/develop/rendering/world>。页面没有明确发布日期；访问 2026-10-02。明确 extraction 读取 world、drawing 只用 immutable render state，及上一帧/下一帧并行目标。
- **[M3] Fabric, Custom Screens（26.2）**：<https://docs.fabricmc.net/develop/rendering/gui/custom-screens>。页面没有明确发布日期；访问 2026-10-02。区分客户端普通 screen 与 server-handled menu，并给 screen/extractRenderState API。
- **[M4] Fabric API 官方 26.2 client GameTest 截图实现**：<https://github.com/FabricMC/fabric-api/blob/6a8ed6bc5a15a01cd6979998fc950f32a67010dc/fabric-client-gametest-api-v1/src/client/java/net/fabricmc/fabric/impl/client/gametest/context/ClientGameTestContextImpl.java#L382-L435>；对应 API <https://github.com/FabricMC/fabric-api/blob/6a8ed6bc5a15a01cd6979998fc950f32a67010dc/fabric-client-gametest-api-v1/src/client/java/net/fabricmc/fabric/api/client/gametest/v1/context/ClientGameTestContext.java>。pin commit 日期 2026-09-18；访问 2026-10-02。仅证明原生 callback 路径与测试调度，不是正常产品捕获流程模板。
- **[M5] NeoForge, Features（页面当前 26.1）**：<https://docs.neoforged.net/docs/rendering/feature>。没有明确发布日期；访问 2026-10-02。提交/渲染阶段与提交后 immutable 要求；不是 26.2 screenshot 签名证明。

### Java / desktop：成熟库接口而非自造协议

- **[J1] GLFW, Input Guide / Clipboard input and output**：<https://www.glfw.org/docs/latest/input_guide.html#clipboard>。页尾 Last update 为 2026-07-31、GLFW 3.5.1；访问 2026-10-02。公开 clipboard 入口处理字符串。
- **[J2] Java SE 25, DataFlavor / imageFlavor**：<https://docs.oracle.com/en/java/javase/25/docs/api/java.datatransfer/java/awt/datatransfer/DataFlavor.html>。版本化官方 API 文档，无页面发布日期；访问 2026-10-02。
- **[J3] Java SE 25, Clipboard**：<https://docs.oracle.com/en/java/javase/25/docs/api/java.datatransfer/java/awt/datatransfer/Clipboard.html>。同上。存在标准传输接口不等于实际游戏平台已经测试。
- **[J4] Java SE 25, ImageIO**：<https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/imageio/ImageIO.html>。同上。成熟 image reader/writer，非自研解码器。

### 同行研究：仅取设计启示，不借用其成功率

下面日期由官方 arXiv API 于 2026-10-02 返回；本次只检索 metadata/摘要与作者官方代码 README，未下载或阅读整篇 PDF。版本不混用。

- **[R1] UFO: A UI-Focused Agent for Windows OS Interaction，2402.07939v5**：<https://arxiv.org/abs/2402.07939v5>。初稿 2024-02-08，v5 更新 2024-05-23。作者官方项目 <https://github.com/microsoft/UFO>。GUI+control information 的双通道，不是对 Minecraft 的直接实验。
- **[R2] UFO2: The Desktop AgentOS，2504.14603v2**：<https://arxiv.org/abs/2504.14603v2>。初稿 2025-04-20，v2 更新 2025-04-25。作者官方 README <https://github.com/microsoft/UFO/blob/main/ufo/README.md>。摘要明确 UIA 与 vision-based parsing 融合，原生 API/GUI 混合路线。
- **[R3] Cradle: Empowering Foundation Agents Towards General Computer Control，2403.03186v3**：<https://arxiv.org/abs/2403.03186v3>。初稿 2024-03-05，v3 更新 2024-07-02。作者官方项目 <https://github.com/BAAI-Agents/Cradle>。GCC 限定截图输入与键鼠输出、不依赖内建 API；README 2024-06-27 update 说明多游戏扩展。OpenAllay 不必采用它的 pixel-only 限制。

### Provider：动态官方 guide / schema

所有访问日期均为 2026-10-02。OpenAI 官方开发者域名当前网络 403，正文通过公开 reader `https://r.jina.ai/https://developers.openai.com/...` 读取，schema 从官方 GitHub raw 直读；源 URL 是以下官方地址。Anthropic/Google guide 直接 HTTP 读取。不是模型 API 实测。OpenAI/Anthropic 页面没有明确作者发布日期；model snapshot 不是文档发布日期。

- **[O1] OpenAI Images and vision**：<https://developers.openai.com/api/docs/guides/images-vision>。输入形状、image-analysis 限额、detail/模型分歧、token 路线、视觉局限。
- **[O2] OpenAI GPT-4.1 model**：<https://developers.openai.com/api/docs/models/gpt-4.1>。text/image input、text output；snapshot `gpt-4.1-2025-04-14` 为模型版本日期。
- **[O3] OpenAI GPT-3.5 Turbo model**：<https://developers.openai.com/api/docs/models/gpt-3.5-turbo>。Image not supported 的反例，不以型号名泛化。
- **[O4] OpenAI 官方 OpenAPI schema**：<https://github.com/openai/openai-openapi/blob/master/openapi.yaml>。`ChatCompletionRequestMessageContentPartImage` 定义 image_url/url。动态分支，无本研究 pin 的发布日期；访问当日核验。
- **[O5] OpenAI API pricing**：<https://developers.openai.com/api/docs/pricing>。定价按具体模型与 token/input 类型；本文不保存通用永久美元价。
- **[A1] Anthropic Vision**：<https://platform.claude.com/docs/en/build-with-claude/vision>。image source、平台限制、当前 28px patch/tier 计数、resize 和局限。
- **[A2] Anthropic Models overview**：<https://platform.claude.com/docs/en/models/overview>。当前模型能力；不是历史/gateway 总承诺。
- **[A3] Anthropic Pricing**：<https://platform.claude.com/docs/en/about-claude/pricing>。所选模型 input price。
- **[A4] Anthropic Vision coordinates**：<https://platform.claude.com/docs/en/build-with-claude/vision-coordinates>。视觉输出、resize/crop 坐标须明确。
- **[G1] Gemini Image understanding**：<https://ai.google.dev/gemini-api/docs/image-understanding>。Last updated 2026-09-23 UTC。Interactions 示例、文件数/MIME、inline 20 MB 与定位事实。
- **[G2] Gemini Media resolution**：<https://ai.google.dev/gemini-api/docs/media-resolution>。Last updated 2026-09-23 UTC。Gemini 3 resolution/token 差异，不套旧 258 token 印象。
- **[G3] Gemini File input methods**：<https://ai.google.dev/gemini-api/docs/file-input-methods>。Last updated 2026-09-23 UTC。与 [G1] 的 inline 100 MB/20 MB 描述冲突保留，不替官方消除。
- **[G4] Gemini Pricing**：<https://ai.google.dev/gemini-api/docs/pricing>。Last updated 2026-10-01 UTC。模型/tier/时间相关，非统一永久价。
- **[C1] OpenAI Computer use**：<https://developers.openai.com/api/docs/guides/tools-computer-use>。
- **[C2] Anthropic Computer use tool**：<https://platform.claude.com/docs/en/agents-and-tools/tool-use/computer-use-tool>。
- **[C3] Gemini Computer use**：<https://ai.google.dev/gemini-api/docs/computer-use>，Last updated 2026-09-23 UTC。三家均由应用提供环境/截图、执行动作、回传结果。


## 12. 本次未核验项

- 没有真实剪贴板图片采集、跨平台 AWT/loader 验证、游戏截图、GPU readback 或 model vision 调用。
- 未确定目标 Minecraft 原生回调线程、NativeImage 所有权/释放策略、frame ID 绑定点；这些不是仅从 Fabric 测试源码就能保证的产品合同。
- 未测量缩放/OCR效果、帧率、图片 token/延迟/账单、上下文压缩与历史保留行为。
- 公开文档限额会变；官方 gateway 与用户配置的兼容 gateway 不同。没有读取用户 endpoint 或配置，也不为未知模型补写“全部支持”。
- 网页检索服务没有配置。研究直接读取公开官方文档与官方 GitHub source；部分旧路径 404、Minecraft.net 页面超时没有作为已核验引用。OpenAI 动态 guide 若被网络限制，按核验到的官方 SDK/API schema/cookbook 与未核验项分开列。
- 没有代码、构建、测试、运行和 commit/push。本文不把研究结论当已批准/已实现能力。
