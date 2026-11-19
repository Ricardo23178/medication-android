package com.zhusixing.medication.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手写的返回栈。
 *
 * 为什么不用 Navigation Compose：
 *   本工程目前只用到「压栈 / 出栈 / 整体替换」三种操作，没有深链、没有多返回栈、
 *   没有转场动画编排。引入导航库要新增一个依赖、对齐一套 DSL，换来的能力此刻用不上。
 *   六十行的栈反而更好排查：任何时刻打印一下 stack 就知道用户在哪。
 *
 * 什么时候该换成 Navigation Compose：
 *   需要外链直接跳到二级页（深链）、需要在 Tab 之间各自维护独立返回栈、
 *   或者要做共享元素转场——这三件事手写会迅速失控。
 *
 * 返回栈永远至少保留一页，所以 [pop] 返回 false 时调用方应当交给系统处理（退出应用）。
 */
class AppNavigator(start: AppRoute) {

    private val _stack = MutableStateFlow(listOf(start))
    val stack: StateFlow<List<AppRoute>> = _stack.asStateFlow()

    val current: AppRoute get() = _stack.value.last()
    val canGoBack: Boolean get() = _stack.value.size > 1

    fun push(route: AppRoute) {
        // 同一页连续压栈没有意义，通常是重复点击，直接忽略。
        if (_stack.value.last() == route) return
        _stack.value = _stack.value + route
    }

    fun pop(): Boolean {
        val pages = _stack.value
        if (pages.size <= 1) return false
        _stack.value = pages.dropLast(1)
        return true
    }

    /** 登录 / 登出时清空整个栈，避免用户按返回键退回上一个身份的地盘。 */
    fun replaceAll(route: AppRoute) {
        _stack.value = listOf(route)
    }
}
