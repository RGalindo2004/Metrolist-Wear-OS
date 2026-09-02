package com.metrolist.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import coil3.compose.AsyncImage
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.AccountPhotoKey
import com.metrolist.music.core.R
import com.metrolist.music.utils.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import androidx.compose.ui.platform.LocalContext

@Composable
fun WearWelcomeScreen(onTimeout: () -> Unit) {
    val context = LocalContext.current
    val accountName by remember(context) {
        context.dataStore.data.map { it[AccountNameKey] ?: "" }
    }.collectAsStateWithLifecycle(initialValue = "")
    
    val accountPhoto by remember(context) {
        context.dataStore.data.map { it[AccountPhotoKey] ?: "" }
    }.collectAsStateWithLifecycle(initialValue = "")

    LaunchedEffect(Unit) {
        delay(1500)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (accountPhoto.isNotEmpty()) {
                AsyncImage(
                    model = accountPhoto,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Text(
                text = if (accountName.isNotEmpty()) {
                    stringResource(R.string.welcome_user, accountName)
                } else {
                    stringResource(R.string.welcome)
                },
                style = MaterialTheme.typography.title3,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
