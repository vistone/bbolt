# bbolt Database Plugin for JetBrains IDEs

## 项目作用

这是一个为 [JetBrains](https://www.jetbrains.com/) 全系列 IDE 开发的数据库可视化插件，用于在 IDE 中直接打开和浏览 **bbolt** 与 **jammdb** 数据库文件。

支持在 IntelliJ IDEA、GoLand、RustRover、PyCharm、WebStorm、CLion、RubyMine、PhpStorm、Android Studio 等 JetBrains 全系 IDE 中以图形化方式查看数据库内容，无需安装额外工具或编写代码。

## 支持的数据库格式

| 格式 | 语言 | 仓库 | 说明 |
|------|------|------|------|
| **bbolt** | Go | https://github.com/etcd-io/bbolt | 经典的 Go 嵌入式 key-value 数据库（原 boltdb） |
| **jammdb** | Rust | https://github.com/jammdb/jammdb | bbolt 的 Rust 实现，文件格式与 bbolt 不同 |

插件会自动检测文件格式（通过 magic number），无需用户手动选择：
- bbolt 文件 magic：`0xED0CDAED`
- jammdb 文件 magic：`0x00ABCDEF`

## 功能特性

- **双格式自动识别**：打开数据库文件时自动检测是 bbolt 还是 jammdb 格式（通过 magic number，不限文件扩展名）
- **Bucket 树浏览**：左侧树形结构展示所有 bucket 及嵌套子 bucket，带颜色区分和父子关系连线
- **Key-Value 表格**：右侧表格展示当前 bucket 中的键值对
- **嵌套 Bucket 导航**：支持任意层级的 bucket 嵌套导航
- **分页加载**：大 bucket 按 500 条/页分页加载，避免一次性加载全部数据导致内存溢出
- **多数据库同时打开**：可在同一个工具窗口中同时打开多个数据库文件，各自独立管理
- **项目文件双击打开**：在 IDE 项目视图双击数据库文件即可直接在工具窗口中打开
- **大 Value 截断预览**：超过 64 KB 的值在表格中截断显示，可按需读取完整内容
- **bbolt 原生支持**：通过 JNA 调用 bbolt 原生库（libbolt.so），完整支持 bbolt 文件格式
- **jammdb 纯 Java 支持**：使用 Java NIO 直接解析 jammdb 文件格式，无需原生库依赖
- **刷新功能**：重新加载数据库内容
- **JetBrains 原生风格 UI**：使用 `ActionToolbar`、`JBTable`、`JBLabel`、`SimpleToolWindowPanel` 等平台组件

## 项目结构

```
bbolt/
├── bolt-jna-core/              # bbolt 的 JNA 绑定（Java 调用 bbolt 原生库）
├── bolt-jna-native/            # bbolt 原生库构建（Go 源码 + cgo）
└── jetbrains-plugin/           # JetBrains IDE 插件主体
    └── src/main/
        ├── java/com/protonail/bolt/intellij/
        │   ├── actions/        # 菜单/工具栏动作（打开数据库、刷新）
        │   ├── driver/         # 驱动集成
        │   ├── file/           # 文件类型识别
        │   ├── icons/          # 插件图标
        │   ├── jammdb/         # jammdb 格式读取器（纯 Java 实现）
        │   │   └── JammdbReader.java
        │   ├── ui/             # UI 组件（工具窗口、树、表格、面板）
        │   │   ├── BoltViewerPanel.java   # 主面板（格式检测 + 分发）
        │   │   ├── BoltToolWindowFactory.java
        │   │   ├── BoltTreeCellRenderer.java
        │   │   └── BucketNode.java
        │   ├── BoltNativeLoader.java      # bbolt 原生库加载
        │   └── BoltStartupActivity.java   # 启动活动
        └── resources/
            ├── META-INF/plugin.xml        # 插件清单
            ├── icons/                     # 图标资源
            └── native/                    # 预编译的 bbolt 原生库
                └── linux-x86-64/libbolt.so
```

## 构建方法

### 前置要求

- JDK 17+
- Gradle 8.7+（项目已包含 gradle wrapper）
- Maven 3.6+（用于构建 bolt-jna-core 依赖）

### 构建步骤

1. **构建 bolt-jna-core 并安装到本地 Maven 仓库**：

```bash
cd bolt-jna-core
mvn install -Dgpg.skip=true
```

2. **构建插件**：

```bash
cd jetbrains-plugin
./gradlew clean buildPlugin
```

3. **构建产物**位于：

```
jetbrains-plugin/build/distributions/bbolt-jetbrains-plugin-<version>.zip
```

## 发布流程

通过推送版本 tag 手动触发 GitHub Actions 自动构建并发布 Release。

### 快速发布

```bash
# 1. 更新版本号（两处必须一致）
#    - jetbrains-plugin/build.gradle.kts        → version = "x.y.z"
#    - jetbrains-plugin/src/main/.../plugin.xml → <change-notes> 顶部加新版本说明

# 2. 提交、打 tag、推送
git add jetbrains-plugin/build.gradle.kts \
        jetbrains-plugin/src/main/resources/META-INF/plugin.xml
git commit -m "release: vx.y.z"
git tag -a vx.y.z -m "vx.y.z: 简短描述"
git push origin master
git push origin vx.y.z      # ← 推送 tag 即触发发布
```

推送 tag 后访问 https://github.com/vistone/bbolt/actions 查看 workflow 状态，完成后 Release 自动出现在 https://github.com/vistone/bbolt/releases 。

### 重新发布同一版本（CI 失败时）

```bash
git push origin :refs/tags/vx.y.z          # 删除远程 tag
git commit -am "fix: ..." && git push origin master
git tag -a vx.y.z -m "vx.y.z: 简短描述"
git push origin vx.y.z
```

### CI Workflow 说明

| Workflow | 触发条件 | 作用 |
|----------|----------|------|
| `CI` | push 到 master 或 PR | 验证构建和测试通过 |
| `Release` | 推送 `v*.*.*` tag | 构建插件并创建 GitHub Release |

## 安装方法

1. 在 JetBrains IDE 中打开：`File` → `Settings` → `Plugins`
2. 点击齿轮图标 ⚙️ → `Install Plugin from Disk...`
3. 选择构建产物 `bbolt-jetbrains-plugin-<version>.zip`
4. 重启 IDE

## 使用方法

### 方式一：通过工具窗口打开
1. 在 IDE 中通过菜单 `View` → `Tool Windows` → `bbolt Database` 打开工具窗口
2. 点击工具栏的 `Open` 按钮，选择数据库文件（任意扩展名，插件会自动识别格式）
3. 左侧树展开 bucket，右侧表格查看键值对
4. 使用底部翻页按钮浏览大型 bucket

### 方式二：通过项目文件双击打开
1. 在 IDE 的项目文件树中找到数据库文件
2. 直接双击该文件
3. 插件自动检测格式（bbolt 或 jammdb），并在 bbolt Database 工具窗口中打开
4. 编辑器区域会显示提示信息，指向工具窗口

### 多数据库管理
- 可同时打开多个数据库文件，每个数据库在树中作为独立的顶级节点
- 点击工具栏的 `Close` 按钮可关闭当前选中的数据库

## 兼容性

- **IDE 版本**：2023.3 及以上（sinceBuild 233，untilBuild 999.*）
- **操作系统**：
  - ✅ Linux x86-64（已内置 libbolt.so）
  - ✅ Windows x86-64（已内置 bolt.dll）
  - ✅ Mac x86-64/arm64（执行 `./build-native.sh` 脚本即可自动编译对应原生库）
- **bbolt 格式**：通过 JNA 原生库支持，对应平台的库已内置/可一键编译
- **jammdb 格式**：纯 Java 实现，全平台无依赖

### 编译 macOS 原生库
在 macOS 机器上执行项目根目录下的 `./build-native.sh` 脚本，会自动编译 x86_64 和 arm64 两个架构的原生库，生成后会自动放到插件资源目录，重新打包插件即可支持 macOS 平台。

## 技术说明

### bbolt 支持
通过 JNA（Java Native Access）调用预编译的 bbolt 原生库（`libbolt.so`），完整支持 bbolt 的 B+tree、事务、bucket 等所有特性。原生库基于 bbolt Go 源码通过 cgo 编译。

### jammdb 支持
使用 Java NIO（`FileChannel` + `ByteBuffer`）直接解析 jammdb 文件格式，无需任何原生库依赖。实现了：
- Meta 页面解析（双 meta 页，选择 tx_id 最高的有效页）
- B+tree 遍历（LEAF / BRANCH 页面）
- Bucket 和嵌套 bucket 导航
- Key-Value 读取

## 版本历史 / 更新日志

### 1.0.8
- 新增：在 IDE 项目视图双击数据库文件即可直接在 bbolt Browser 工具窗口中打开
- 通过 magic number 自动检测格式，任意文件扩展名均可
- 为二进制数据库文件提供轻量占位编辑器，替换默认文本编辑器，并显示指向工具窗口的提示

### 1.0.7
- 树视图优化：节点使用不同颜色区分数据库文件（深蓝）、bucket（深青）和 key（默认前景色），不再全部灰色
- 启用经典树状连线（`JTree.lineStyle=Angled`），父子关系一目了然

### 1.0.6
- 替换项目图标为基于 etcd logo 的矢量 SVG（去除 "etcd" 文字）
- SVG 包含六边形齿轮外轮廓、中心六边形孔和两个"眼睛"，使用 `fill-rule="evenodd"` 正确嵌套
- 矢量格式可任意尺寸（13x13、16x16、32x32）缩放，无模糊变形
- 所有主要图标位置（工具窗口、数据库节点、bucket 节点、key 节点、文件类型）统一使用该图标

### 1.0.5
- UI 全面适配 JetBrains 原生风格：用 `ActionToolbar` + `AnAction` 替换原始 `JToolBar`/`JButton`（Open/Close/Refresh）
- 用 `SimpleToolWindowPanel` 替换 `JPanel`，用 `JBTable` 替换 `JTable`（合适的行高、空状态文本、表头样式）
- 用 `JBLabel` 替换 `JLabel`，翻页按钮使用平台图标（`AllIcons.Actions.Back/Forward`）
- 移除文件选择器的 `.db` 扩展名过滤，任意扩展名均可打开，格式由 magic number 自动检测

### 1.0.4
- 支持同时打开多个数据库文件，每个数据库作为独立的顶级树节点，拥有独立的 bucket 和分页状态
- 新增 "Close" 工具栏按钮，可关闭选中的数据库
- 修复分页按钮状态更新问题：每次加载后可靠地启用/禁用按钮，加入 stale-result 防护

### 1.0.3
- 大 bucket 分页支持：按 500 条/页加载，避免一次性加载全部数据导致 OutOfMemoryError
- 新增上一页/下一页导航控件
- 超过 64 KB 的值在预览模式中截断，可通过 `getValue()` 按需读取完整内容

### 1.0.2
- 修复读取包含跨多页大值（overflow pages）的 jammdb bucket 时出现 `BufferUnderflowException` 的问题
- 读取器现在会分配覆盖所有 overflow pages 的缓冲区再解析 leaf elements
- 新增覆盖大值场景的单元测试

### 1.0.1
- 新增 jammdb 数据库格式支持，与现有 bbolt 支持并存
- 通过 magic number 自动检测格式

### 1.0.0
- 初始版本：JDBC 驱动集成和 bbolt 数据库可视化浏览器
