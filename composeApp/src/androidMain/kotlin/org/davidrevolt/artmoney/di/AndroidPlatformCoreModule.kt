package org.davidrevolt.artmoney.di

import org.davidrevolt.artmoney.search.SearchAlgorithm
import org.davidrevolt.artmoney.service.AndroidMemoryEditor
import org.davidrevolt.artmoney.service.AndroidProcessManager
import org.davidrevolt.artmoney.service.MemoryEditor
import org.davidrevolt.artmoney.service.ProcessManager
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

internal actual val platformCoreModule = module {
    // Context will be provided when Koin starts because on MainActivity I used androidContext param
    singleOf(::AndroidProcessManager) { bind<ProcessManager>() }
    single<MemoryEditor> {
        AndroidMemoryEditor(
            get<SearchAlgorithm>(),
            get(named(IO_DISPATCHER)),
            get(named(DEFAULT_DISPATCHER))
        )
    }
}