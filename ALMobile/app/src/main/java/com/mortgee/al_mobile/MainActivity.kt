package com.mortgee.al_mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.mortgee.al_mobile.data.network.ApiClient
import com.mortgee.al_mobile.ui.catalogue.PropertyCatalogueRoute
import com.mortgee.al_mobile.ui.detail.PropertyDetailRoute
import com.mortgee.al_mobile.ui.theme.ALMobileTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var apiClient: ApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ALMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var selectedPropertyId by remember { mutableStateOf<String?>(null) }

                    selectedPropertyId?.let { propertyId ->
                        PropertyDetailRoute(
                            propertyId = propertyId,
                            onBackClick = { selectedPropertyId = null }
                        )
                    } ?: run {
                        PropertyCatalogueRoute(
                            onPropertyClick = { id -> selectedPropertyId = id }
                        )
                    }
                }
            }
        }
    }
}
