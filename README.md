# 项目记录

## 项目简介
HabitTracker 是一个基于 Jetpack Compose 的习惯打卡 App，支持 habit 创建、每日打卡、连续打卡统计、跨天刷新和 Statistics 数据统计。项目定位是 Android 初/中级开发工程师简历项目，重点展示本地数据、响应式 UI、状态管理、测试和工程演进能力。

## 技术栈
- Kotlin
- Jetpack Compose
- Material3
- MVVM
- Room
- Flow / StateFlow / SharedFlow
- Navigation Compose
- JVM unit test

## 当前核心功能
- Habit 列表展示。
- 新建 habit。
- 今日打卡 / 取消打卡。
- 今日状态跨天自动刷新。
- Current streak 计算。
- Statistics 页面：今日完成数、近 7 天完成率、最高 streak、总打卡次数、habit 数量。
- Dashboard 空状态和 checked 状态基础 polish。

## 架构设计
```text
Compose UI
   ↓
ViewModel
   ↓
UseCase / Repository
   ↓
Room DAO
   ↓
SQLite
```


## 关键技术点
1. Room + Flow 响应式 UI
   - DAO 暴露 Flow 数据源。
   - ViewModel 使用 StateFlow 组装页面级 UI state。
   - Compose 使用 lifecycle-aware collect 观察状态。

2. 打卡写入一致性
   - `RecordEntity` 使用 `(habitId, date)` 唯一索引。
   - `RecordDao.setRecordChecked()` 使用 `@Transaction` 包住读写。
   - Repository 只委托 DAO 事务入口，避免业务层散落 read-modify-write。

3. 跨天刷新
   - `systemTodayFlow()` 在本地日期变化时发出新的 LocalDate。
   - Dashboard 与 Statistics 共用 today source。
   - Streak 和今日完成状态都基于同一个 today 计算。

4. Optimistic UI lifecycle
   - `SetTodayHabitCheckedUseCase.Command` 表达用户打卡意图。
   - `DomainEvent` 表达 OptimisticApplied、Superseded、Confirmed、Reverted、Rejected。
   - 支持快速重复点击时保留 latest pending target。
   - source convergence timeout 或 persist failure 会触发 rollback event。

5. 可测试的业务逻辑
   - `calculateStreak()` 覆盖空记录、连续记录、断档、重复日期、今日未完成。
   - `calculateStats()` 覆盖空数据、单个 habit、多个 habit。
   - UseCase 覆盖 confirmed、cleanup、pending drain、timeout rollback、persist failure rollback。

## 已完成提交主线
- 初始化 Git baseline。
- 清理 MainActivity / Navigation 命名。
- 精简旧 HabitViewModel，只保留新增 habit 职责。
- 补充 streak/date 单测。
- 增加 DAO transaction 写入入口。
- 完成 optimistic lifecycle。
- 接入跨天 today source。
- 增加 Dashboard failure event 和 snackbar。
- 增加 Statistics 聚合和页面。
- 打磨 Dashboard 空状态与 checked 状态。
