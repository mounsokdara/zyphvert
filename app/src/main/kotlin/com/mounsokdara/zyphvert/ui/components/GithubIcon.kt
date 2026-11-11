package com.mounsokdara.zyphvert.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mounsokdara.zyphvert.R

@Composable
fun GithubIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = LocalContentColor.current) {
    Icon(painterResource(R.drawable.ic_github), contentDescription = "GitHub", modifier = modifier.size(size), tint = tint)
}
