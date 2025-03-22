package org.davidrevolt.artmoney

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.davidrevolt.artmoney.di.coreModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.compose.KoinApplication


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
            setContent {
                KoinApplication(
                    application = {
                        modules(coreModule)
                        androidContext(applicationContext)
                        androidLogger() // LogCat Prints for koin
                    }
                ) {
                    App()
                }
            }


    }
}


