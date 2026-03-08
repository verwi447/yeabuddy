package com.example.yeabuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.yeabuddy.ui.theme.YeabuddyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YeabuddyTheme {
                var gender by remember { mutableStateOf("") }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (gender.isEmpty()) {
                        // Ekran wyboru płci
                        GenderSelectionScreen(
                            onGenderSelected = { selected -> gender = selected },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        // Ekran treningu
                        WorkoutScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}

// =====================
// EKRAN WYBORU PŁCI
// =====================
@Composable
fun GenderSelectionScreen(
    onGenderSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedGender by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Wybierz swoją płeć:", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { selectedGender = "Mężczyzna"; onGenderSelected("Mężczyzna") }) {
                Text("Mężczyzna")
            }
            Button(onClick = { selectedGender = "Kobieta"; onGenderSelected("Kobieta") }) {
                Text("Kobieta")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedGender.isNotEmpty()) {
            Text("Wybrano: $selectedGender")
        }
    }
}

// =====================
// EKRAN TRENINGU
// =====================
@Composable
fun WorkoutScreen(modifier: Modifier = Modifier) {
    var statusText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Moja aplikacja o siłowni", modifier = Modifier.padding(bottom = 20.dp))

        Button(onClick = { statusText = "Trening rozpoczęty 💪" }) {
            Text("Rozpocznij trening")
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (statusText.isNotEmpty()) {
            Text(text = statusText)
        }
    }
}

// =====================
// PODGLĄD W ANDROID STUDIO
// =====================
@Preview(showBackground = true)
@Composable
fun GenderPreview() {
    YeabuddyTheme {
        GenderSelectionScreen(onGenderSelected = {})
    }
}

@Preview(showBackground = true)
@Composable
fun WorkoutPreview() {
    YeabuddyTheme {
        WorkoutScreen()
    }
}