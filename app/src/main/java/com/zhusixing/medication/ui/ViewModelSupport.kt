package com.zhusixing.medication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * 极简的 ViewModel 工厂。
 *
 * 各页面的 ViewModel 都靠构造器拿依赖，没有无参构造，所以必须显式给工厂。
 * 用 lambda 包一层比每个 ViewModel 写一个伴生 Factory 少很多样板代码。
 */
fun <VM : ViewModel> simpleViewModelFactory(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return create() as T
        }
    }
