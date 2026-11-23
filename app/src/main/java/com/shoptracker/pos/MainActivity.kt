package com.shoptracker.pos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.shoptracker.pos.ui.App
import com.shoptracker.pos.ui.ShopTheme
import com.shoptracker.pos.ui.UserProfile

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UserProfile.load(this)
        setContent {
            ShopTheme {
                App(viewModel)
            }
        }
    }
}
