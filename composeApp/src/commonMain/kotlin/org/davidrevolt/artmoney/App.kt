package org.davidrevolt.artmoney


import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import artmoney.composeapp.generated.resources.Res
import artmoney.composeapp.generated.resources.background
import org.davidrevolt.artmoney.ui.ArtMoney
import org.jetbrains.compose.resources.painterResource

@Composable
fun App() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(Res.drawable.background),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.1f) // Subtle, semi-transparent
                    .align(Alignment.Center),
                contentScale = ContentScale.FillBounds // stretch logo when max size window
            )
            ArtMoney()
        }
    }
}



