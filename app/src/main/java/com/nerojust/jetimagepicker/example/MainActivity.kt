package com.nerojust.jetimagepicker.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var selectedTab by remember { mutableStateOf(0) }

            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { androidx.compose.material3.Text("Image") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { androidx.compose.material3.Text("Video") })
                }
                when (selectedTab) {
                    0 -> ImagePickerScreen(Modifier.fillMaxSize())
                    1 -> VideoPickerScreen(Modifier.fillMaxSize())
                }
            }
        }
    }
}
