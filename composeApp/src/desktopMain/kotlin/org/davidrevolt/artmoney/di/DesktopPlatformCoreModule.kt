package org.davidrevolt.artmoney.di

import org.davidrevolt.artmoney.search.SearchAlgorithm
import org.davidrevolt.artmoney.service.DesktopMemoryEditor
import org.davidrevolt.artmoney.service.DesktopProcessManager
import org.davidrevolt.artmoney.service.MemoryEditor
import org.davidrevolt.artmoney.service.ProcessManager
import org.koin.core.qualifier.named
import org.koin.dsl.module


internal actual val platformCoreModule = module {
    single<ProcessManager> { DesktopProcessManager(get(named(IO_DISPATCHER))) }
    single<MemoryEditor> {
        DesktopMemoryEditor(
            get<SearchAlgorithm>(),
            get(named(IO_DISPATCHER)),
            get(named(DEFAULT_DISPATCHER))
        )
    }
}