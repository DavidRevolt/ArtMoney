package org.davidrevolt.artmoney.di


import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.davidrevolt.artmoney.search.BoyerMooreSearchV1
import org.davidrevolt.artmoney.search.SearchAlgorithm
import org.davidrevolt.artmoney.ui.ArtMoneyViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

const val IO_DISPATCHER = "ioDispatcher"
const val DEFAULT_DISPATCHER = "defaultDispatcher"

//commonMain module
private val commonCoreModule = module {
    single<CoroutineDispatcher>(named(IO_DISPATCHER)) { Dispatchers.IO }
    single<CoroutineDispatcher>(named(DEFAULT_DISPATCHER)) { Dispatchers.Default }
    viewModelOf(::ArtMoneyViewModel)
    singleOf(::BoyerMooreSearchV1) { bind<SearchAlgorithm>() }
}

internal expect val platformCoreModule: Module


val coreModule: Module
    get() = module {
        includes(commonCoreModule + platformCoreModule)
    }