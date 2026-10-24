package com.campus.bustracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.campus.bustracker.data.repository.SessionRepository
import com.campus.bustracker.ui.navigation.BusTrackerNavGraph
import com.campus.bustracker.ui.theme.BusTrackerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BusTrackerTheme {
                // Guardrail 5: DriverViewModel and StudentViewModel are strictly scoped to their respective
                // screen composable destinations inside BusTrackerNavGraph, NEVER instantiated eagerly here.
                BusTrackerNavGraph(sessionRepository = sessionRepository)
            }
        }
    }
}
