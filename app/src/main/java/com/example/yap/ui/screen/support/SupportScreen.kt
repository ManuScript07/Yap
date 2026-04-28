package com.example.yap.ui.screen.support

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yap.R
import com.example.yap.ui.components.BaseTopAppBar
import com.example.yap.ui.theme.LocalAdditionColors
import com.example.yap.ui.theme.LocalBaseScale
import com.example.yap.util.openUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onBack: () -> Unit,
    viewModel: SupportViewModel = viewModel()
) {
    val context = LocalContext.current
    val baseScale = LocalBaseScale.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BaseTopAppBar(
                title = stringResource(R.string.support_title),
                onBack = onBack,
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp * baseScale)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(32.dp * baseScale))

            Text(
                text = stringResource(R.string.support_have_problems),
                fontSize = 23.sp * baseScale,
                fontWeight = FontWeight.Medium,
                color = LocalAdditionColors.current.secondTextColor
            )

            Spacer(modifier = Modifier.height(12.dp * baseScale))


            Text(
                text = stringResource(R.string.support_text),
                fontSize = 20.sp * baseScale,
                lineHeight = 24.sp * baseScale,
                color = LocalAdditionColors.current.secondTextColor
            )

            Spacer(modifier = Modifier.height(32.dp * baseScale))

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp * baseScale),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialIconButton(
                    iconRes = R.drawable.telegram_svg,
                    onClick = { openUrl(context, viewModel.telegramUrl) },
                    baseScale = baseScale,
                    isCircle = true
                )
                SocialIconButton(
                    iconRes = R.drawable.github_svg,
                    onClick = { openUrl(context, viewModel.gitHubUrl) },
                    baseScale = baseScale,
                    isCircle = false
                )
            }
        }
    }
}




