# Android Habit Tracker

一个基于 Kotlin、Jetpack Compose、Room、Flow / StateFlow 和 MVVM 架构实现的本地习惯打卡 App。

项目支持习惯创建、每日打卡、连续打卡统计、跨天刷新和 Statistics 数据统计。项目重点不只是完成基础功能，也围绕本地数据一致性、响应式 UI 状态管理、Optimistic UI、业务逻辑测试和工程演进进行了设计。

本项目用于实践 Android 现代开发技术栈下的完整功能闭环，重点覆盖本地数据持久化、响应式 UI 状态管理、业务逻辑测试、数据一致性处理和基础工程化组织。

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
- 当前连续打卡天数 Current Streak 计算。
- Statistics 页面：今日完成数、近 7 天完成率、最高 streak、总打卡次数、habit 数量。
- Dashboard 空状态与已打卡状态的基础视觉优化。

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

## 开发演进记录

项目开发过程中主要经历了以下阶段：

- 完成基础功能闭环，包括 habit 创建、列表展示和每日打卡。
- 引入 Room 本地持久化，并通过 Flow 驱动响应式 UI 更新。
- 清理 MainActivity / Navigation 命名，优化页面职责划分。
- 精简旧 HabitViewModel，只保留新增 habit 职责。
- 补充 streak / date 相关单元测试。
- 增加 DAO transaction 写入入口，收敛打卡写入一致性。
- 完成 Optimistic UI lifecycle，处理快速重复点击、pending target、确认与回滚。
- 接入跨天 today source，统一 Dashboard 与 Statistics 的日期来源。
- 增加 Dashboard failure event 和 snackbar 反馈。
- 增加 Statistics 聚合和页面展示。
- 打磨 Dashboard 空状态与已打卡状态的基础视觉表现。