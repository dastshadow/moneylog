# Git 入门 —— 今天你亲手把项目放进了"时间机器"

> 日期：2026-10-17（Day12 下午接续）
> 环境：Git 2.55.0.windows.3，Git Bash（MINGW64），项目 `D:\dev\moneylog`

---

## 一、Git 到底是什么（先把概念弄清楚）

**不是网盘，不是云盘，也不是备份工具。**

它在你项目里建一个隐藏文件夹 `.git`，专门存"某个时刻的完整快照"。

### 三个区（这是 Git 最核心的模型）

```
   工作区                      暂存区                    仓库
 （你看到的文件）             （待拍照区）              （.git 里的快照）
      │                          │                        │
      │  git add                 │                        │
      ├─────────────────────────►│                        │
      │                          │  git commit            │
      │                          ├───────────────────────►│
      │                          │                        │
      │◄─────────────────────────┴────────────────────────┤
      │        git restore（丢弃未提交的改动，回到快照）    │
```

| 区 | 是什么 | 类比 |
|---|---|---|
| **工作区** | 磁盘上你能编辑的文件 | 现场的人 |
| **暂存区** | 已经 `add`、准备提交的改动 | 摆到镜头前准备拍照的人 |
| **仓库** | `.git` 里的一堆历史快照 | 相册 |

**为什么要中间那个暂存区？** 因为一次改动常混着几件不相关的事。暂存区让你**只挑其中一部分**提交，历史更干净。
（个人开发时可以先不管它，直接 `add .` + `commit`，但要知道它的存在。）

---

## 二、今天的完整操作（可以照着复现）

### 1. 建仓库

```bash
cd /d D:\dev\moneylog      # 必须在项目根目录！
git init
```
输出：`Initialized empty Git repository in D:/dev/moneylog/.git/`

> ⚠️ **不要在 `D:\dev` 或用户目录 init** —— 那会把整片区域变成仓库。

### 2. 先看状态（永远的第一步）

```bash
git status
git status --short      # 紧凑格式，日常最常用
```

**短格式的两个字符含义**：

```
 M path/to/file     ← 前面空格 = 暂存区干净；M = 工作区改了（未 add）
M  path/to/file     ← M 在左 = 已 add（未 commit）
MM path/to/file     ← 两边都改过
?? path/to/file     ← 新文件，Git 还不管它
```

**规则**：左边管暂存区，右边管工作区。

### 3. 干跑检查（提交前必做）

```bash
git add -n .                              # -n = --dry-run，只说不做
git add -n . | wc -l                      # 数一下会加多少文件
git add -n . | grep -E "node_modules|target/|dist/"   # 查有没有漏网的大家伙
```

> **为什么不直接 `git add .`**：那一刻如果 `.gitignore` 有漏洞，
> 会把 `node_modules`（几万个文件、几百 MB）加进去 —— 而且**提交过一次就永远留在历史里**。

### 4. 加入暂存区

```bash
git add .              # 加当前目录下所有未被忽略的
git status             # 再确认一遍，变绿色（Changes to be committed）
```

### 5. 提交（存档）

```bash
git commit -m "初始提交：个人记账本 MoneyLog（账户/账单/统计/鉴权/数据隔离/部署）"
```

**这次的成果**：
```
72 files changed, 13210 insertions(+)
node_modules 被跟踪数量：0   ← .gitignore 生效
```

**commit 说明怎么写**：

```
❌ "update"          ← 等于没写
❌ "改了点东西"       ← 半年后你自己都看不懂
✅ "修复账单金额为负时余额计算错误"
✅ "初始提交：个人记账本 MoneyLog（……）"
```

> 半年后你想查"这个功能什么时候做的、为什么这么改"，**只能靠这些说明**。

### 6. 看历史

```bash
git log --oneline           # 一行一个提交
git show --stat HEAD        # 最近一次提交改了哪些文件、加了多少行
```

`14da9e2` 这类 7 位字符串是**提交 ID**，以后回退靠它。
`HEAD` = 当前所在的最新提交；`HEAD~1` = 上一个。

### 7. 看改动

```bash
git diff                    # 工作区 vs 上次提交（未 add 的改动）
git diff --cached           # 暂存区 vs 上次提交（已 add 的改动）
git diff --stat             # 只看哪些文件变了、加删多少行
```

**怎么读 diff**：

```diff
@@ -26,6 +26,6 @@ public class PingController {
 public Result<String> ping() {
-        return Result.ok("pong");
+        return Result.ok("pong 错误");
 }
```

| 那一行 | 意思 |
|---|---|
| `@@ -26,6 +26,6 @@` | 改动位置：旧版本第 26 行起取 6 行，新版本第 26 行起取 6 行；后面是所在类的提示 |
| 行首一个空格 | 没变，只是上下文 |
| 行首 `-` | 旧内容（被删/被替换） |
| 行首 `+` | 新内容（新加的） |

### 8. 还原（丢弃未提交的改动）

```bash
git restore backend/src/main/java/com/wuweiqiu/moneylog/controller/PingController.java
git status --short      # 变空 = 恢复成功
```

老写法 `git checkout -- <文件>` 效果一样。`restore` 是 Git 2.23 之后的新命令，**只管一件事，更安全**。

> ⚠️ **`git restore` 不可逆**。没 add 也没 commit 的改动，扔掉就真没了。
> 所以纪律是：**动手大改之前，先 commit 一次。**

---

## 三、今天踩的两个坑

### 坑 1：`git add` 刷一大堆 warning

```
warning: in the working copy of 'backend/pom.xml', LF will be replaced by CRLF 
the next time Git touches it
```

**原因**：`core.autocrlf = true`（Windows 装 Git 的默认选项）。

- 提交时：`CRLF` → `LF`（仓库里统一存 LF）
- 检出时：`LF` → `CRLF`（你电脑上是 Windows 格式）

**为什么要统一**：Windows 用 `CRLF`、Linux/macOS 用 `LF`。不统一的话，
同一个文件在两个系统上会被 Git 认为"每个字节都变了"，一次改一个字却显示整个文件全改。

**结论**：这句 warning **无害**，是"我记下了，下次帮你换成 Windows 格式"的告知。
文件多所以每个提示一次，只在第一次 add 时出现。

**想让它闭嘴**：项目根目录加 `.gitattributes`：
```
* text=auto eol=lf
```

### 坑 2：`git diff` 没反应

**真因**：文件根本没保存（编辑器里改了，磁盘上没变，而 Git 只看磁盘）。

**`git diff` 没输出 = 真的没差别**，不是命令坏了。

**排查方法**：
| 手段 | 怎么看 |
|---|---|
| `ls -la` 看修改时间 | 时间还是几天前的 → 没写进去 |
| VS Code 标签页 | 文件名右侧实心圆点 `●` = 未保存；`Ctrl+S` 后变 `×` |

**顺带一个坑**：**别用记事本改代码**。记事本保存会把 `LF` 改成 `CRLF`，
于是 Git 认为整个文件每一行都变了（改一个词 → diff 显示几十行）。**用 VS Code**。

---

## 四、以后要遵守的三条纪律

1. **`add` 之前先 `status`** —— 确认到底要提交什么
2. **大改之前先 `commit`** —— 存一个好的状态，改崩了能回来
3. **commit 说明写清楚** —— 写给三个月后的自己看

---

## 五、今天没学的（不用急）

| 主题 | 什么时候需要 |
|---|---|
| **分支（branch）** | 要同时做好几个功能、或者团队协作时 |
| **远端（GitHub / Gitee）** | 想备份到云端、想让别人看到、想放简历时 |
| **冲突解决** | 两个人改了同一个文件的同一处 |
| **`.gitattributes`** | 想消掉 CRLF 警告，或者跨平台协作时 |

> **用不上的知识留不住。** 等真正遇到再学，一次就能记住。

---

## 六、我自己的笔记

> （这块留给我自己写。用我自己的话，写我真正搞懂的东西。）
