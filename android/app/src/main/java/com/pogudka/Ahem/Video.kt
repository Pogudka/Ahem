package com.pogudka.Ahem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun VideoScreen() {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(ic(P.bg)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(28.dp, 40.dp).background(ic(P.surface), RoundedCornerShape(20.dp)).border(1.dp, ic(P.border), RoundedCornerShape(20.dp)).padding(28.dp, 40.dp)) {
            Icon(painter = painterResource(R.drawable.ic_video), contentDescription = null, tint = ic(P.text), modifier = Modifier.size(48.dp))
            Text("Видео скоро", color = ic(P.text), fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Text("Здесь появится лента клипов", color = ic(P.textSec), fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
