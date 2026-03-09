package com.example.yeabuddy

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.*
import coil.compose.AsyncImage
import com.example.yeabuddy.ui.theme.YeabuddyTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// =====================
// MINIMALIST DARK COLORS
// =====================
val MinBackground = Color(0xFF000000)
val MinSurface = Color(0xFF121212)
val MinAccent = Color(0xFFFFFFFF)
val MinSecondary = Color(0xFF222222)
val MinGray = Color(0xFF777777)
val MinRed = Color(0xFFFF3B30)
val MinGreen = Color(0xFF34C759)
val MinBlue = Color(0xFF0A84FF)
val MinGold = Color(0xFFFFD700)

// =====================
// UTILS
// =====================
fun saveImageLocally(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "profile_pic_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
        file.absolutePath
    } catch (e: Exception) { null }
}

// =====================
// ROOM ENTITIES
// =====================
@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0
)

@Entity(tableName = "exercise_records")
data class ExerciseRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: Int,
    val exerciseName: String,
    val reps: String = "",
    val weight: String = "",
    val isDone: Boolean = false
)

@Dao
interface WorkoutDao {
    @Insert suspend fun insertSession(session: WorkoutSession): Long
    @Update suspend fun updateSession(session: WorkoutSession)
    @Insert suspend fun insertRecord(record: ExerciseRecord)
    @Query("SELECT * FROM workout_sessions ORDER BY date DESC") suspend fun getAllSessions(): List<WorkoutSession>
    @Query("SELECT * FROM exercise_records WHERE sessionId = :sid") suspend fun getRecordsForSession(sid: Int): List<ExerciseRecord>
    @Query("SELECT * FROM exercise_records WHERE sessionId IN (SELECT id FROM workout_sessions WHERE date > :after)") suspend fun getRecentRecords(after: Long): List<ExerciseRecord>
    @Query("DELETE FROM workout_sessions") suspend fun deleteAllSessions()
    @Query("DELETE FROM exercise_records") suspend fun deleteAllRecords()
    @Query("SELECT MAX(CAST(weight AS FLOAT)) FROM exercise_records WHERE exerciseName = :name") suspend fun getExercisePR(name: String): Float?
}

@Database(entities = [WorkoutSession::class, ExerciseRecord::class], version = 14, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "yeabuddy_db")
                    .fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// =====================
// NEWS MODELS
// =====================
data class NewsItem(val title: String, val imageUrl: String, val date: String)

val NEWS_DATA = listOf(
    NewsItem("Mr. Olympia 2024 results!", "https://images.unsplash.com/photo-1534438327276-14e5300c3a48", "Oct 15, 2024"),
    NewsItem("New training techniques for legs", "https://images.unsplash.com/photo-1434494878577-86c23bcb06b9", "Oct 18, 2024"),
    NewsItem("Bodybuilding championship in Warsaw", "https://images.unsplash.com/photo-1583454110551-21f2fa202214", "Oct 20, 2024")
)

// =====================
// MAIN ACTIVITY
// =====================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        setContent {
            YeabuddyTheme {
                val context = LocalContext.current
                var isFirstRun by remember { mutableStateOf(sharedPref.getString("user_nick", "").isNullOrEmpty()) }
                var userNick by remember { mutableStateOf(sharedPref.getString("user_nick", "") ?: "") }
                var userGender by remember { mutableStateOf(sharedPref.getString("user_gender", "") ?: "") }
                var userHeight by remember { mutableStateOf(sharedPref.getString("user_height", "") ?: "") }
                var userWeight by remember { mutableStateOf(sharedPref.getString("user_weight", "") ?: "") }
                var userAge by remember { mutableStateOf(sharedPref.getString("user_age", "") ?: "") }
                var userBio by remember { mutableStateOf(sharedPref.getString("user_bio", "") ?: "") }
                var userImageUri by remember { mutableStateOf(sharedPref.getString("user_image", "") ?: "") }

                if (isFirstRun) {
                    LoginMenu(onProfileSaved = { nick, gender, height, weight, age, bio, image ->
                        val finalPath = if (image.isNotEmpty() && image.startsWith("content://")) saveImageLocally(context, Uri.parse(image)) ?: image else image
                        userNick = nick; userGender = gender; userHeight = height; userWeight = weight; userAge = age; userBio = bio; userImageUri = finalPath
                        sharedPref.edit().apply {
                            putString("user_nick", nick); putString("user_gender", gender); putString("user_height", height)
                            putString("user_weight", weight); putString("user_age", age); putString("user_bio", bio); putString("user_image", finalPath)
                            apply()
                        }
                        isFirstRun = false
                    })
                } else {
                    MainNavigation(
                        userNick, userGender, userHeight, userWeight, userAge, userBio, userImageUri,
                        onUpdate = { nick, gender, h, w, age, bio, img ->
                            val finalPath = if (img.startsWith("content://")) saveImageLocally(context, Uri.parse(img)) ?: img else img
                            userNick = nick; userGender = gender; userHeight = h; userWeight = w; userAge = age; userBio = bio; userImageUri = finalPath
                            sharedPref.edit().apply {
                                putString("user_nick", nick); putString("user_gender", gender); putString("user_height", h)
                                putString("user_weight", w); putString("user_age", age); putString("user_bio", bio); putString("user_image", finalPath)
                                apply()
                            }
                        },
                        onDeleteAccount = {
                            sharedPref.edit().clear().apply()
                            CoroutineScope(Dispatchers.IO).launch {
                                val dao = AppDatabase.getDatabase(context).workoutDao()
                                dao.deleteAllSessions()
                                dao.deleteAllRecords()
                            }
                            isFirstRun = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainNavigation(nick: String, gender: String, h: String, w: String, age: String, bio: String, img: String, onUpdate: (String, String, String, String, String, String, String) -> Unit, onDeleteAccount: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(1) } // Default to Workout

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MinSurface, contentColor = MinAccent) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Analytics, null) },
                    label = { Text("Tracker", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MinBlue, unselectedIconColor = MinGray, selectedTextColor = MinBlue, unselectedTextColor = MinGray, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.FitnessCenter, null) },
                    label = { Text("Workout", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MinBlue, unselectedIconColor = MinGray, selectedTextColor = MinBlue, unselectedTextColor = MinGray, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Newspaper, null) },
                    label = { Text("News", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MinBlue, unselectedIconColor = MinGray, selectedTextColor = MinBlue, unselectedTextColor = MinGray, indicatorColor = Color.Transparent)
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("Profile", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = MinBlue, unselectedIconColor = MinGray, selectedTextColor = MinBlue, unselectedTextColor = MinGray, indicatorColor = Color.Transparent)
                )
            }
        }
    ) { p ->
        Box(modifier = Modifier.padding(p)) {
            val context = LocalContext.current
            val dao = remember { AppDatabase.getDatabase(context).workoutDao() }
            val sessions by produceState<List<WorkoutSession>>(initialValue = emptyList()) {
                value = dao.getAllSessions()
            }

            when (selectedTab) {
                0 -> TrackerScreen(nick, img, h, w, onGoToBadges = { })
                1 -> WorkoutScreen(nick, img) { selectedTab = 3 }
                2 -> NewsScreen()
                3 -> ProfileScreen(nick, gender, h, w, age, bio, img, { selectedTab = 1 }, onUpdate, onDeleteAccount)
            }
        }
    }
}

// =====================
// COMPONENTS
// =====================
@Composable
fun MinTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text) {
    Column(modifier = modifier) {
        Text(label.uppercase(), color = MinGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = MinAccent, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            cursorBrush = SolidColor(MinAccent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) Text("Enter...", color = MinSecondary, fontSize = 16.sp)
                    innerTextField()
                }
            }
        )
        HorizontalDivider(color = MinSecondary, thickness = 1.dp)
    }
}

// =====================
// SCREENS
// =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(nick: String, img: String, heightStr: String, weightStr: String, onGoToBadges: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).workoutDao() }
    val sessions by produceState<List<WorkoutSession>>(initialValue = emptyList()) {
        value = dao.getAllSessions()
    }
    
    val recentRecords by produceState<List<ExerciseRecord>>(initialValue = emptyList()) {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        value = dao.getRecentRecords(sevenDaysAgo)
    }

    var selectedSession by remember { mutableStateOf<WorkoutSession?>(null) }
    var sessionRecords by remember { mutableStateOf<List<ExerciseRecord>>(emptyList()) }
    val scrollState = rememberScrollState()

    val heightM = heightStr.toDoubleOrNull()?.div(100) ?: 0.0
    val weightKg = weightStr.toDoubleOrNull() ?: 0.0
    val bmi = if (heightM > 0) weightKg / (heightM * heightM) else 0.0
    val bmiStatus = when {
        bmi < 18.5 -> "Niedowaga"
        bmi < 25.0 -> "Norma"
        bmi < 30.0 -> "Nadwaga"
        else -> "Otyłość"
    }

    val rank = when {
        sessions.size < 5 -> "REKRUT"
        sessions.size < 15 -> "DZIK"
        sessions.size < 30 -> "KOKS"
        else -> "LEGENDA"
    }

    Column(modifier = Modifier.fillMaxSize().background(MinBackground).verticalScroll(scrollState)) {
        // Nagłówek profilu
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(MinSecondary).clickable { onGoToBadges() }) {
                if (img.isNotEmpty()) AsyncImage(model = img, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, tint = MinGray, modifier = Modifier.padding(24.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(nick.uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Black, color = MinAccent, letterSpacing = 2.sp)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(rank, fontSize = 12.sp, color = MinBlue, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(12.dp))
                Surface(color = MinSecondary, shape = RoundedCornerShape(6.dp)) {
                    Text("🏅 ${sessions.size}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, color = MinGold, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MinSurface),
            border = BorderStroke(1.dp, MinSecondary)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("WORKOUT CALENDAR", color = MinGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(16.dp))
                CalendarGrid(sessions) { session ->
                    selectedSession = session
                    CoroutineScope(Dispatchers.IO).launch { sessionRecords = dao.getRecordsForSession(session.id) }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = MinSurface),
            border = BorderStroke(1.dp, MinSecondary)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TRAINED MUSCLES", color = MinGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    TrainedMusclesList(recentRecords, dao)
                }
                Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    MuscleHumanoid(recentRecords)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MinSurface), border = BorderStroke(1.dp, MinSecondary)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("BMI", color = MinGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(String.format(Locale.US, "%.1f", bmi), fontSize = 18.sp, fontWeight = FontWeight.Black, color = MinAccent)
                    Text(bmiStatus, fontSize = 10.sp, color = MinGreen, fontWeight = FontWeight.Bold)
                }
            }
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MinSurface), border = BorderStroke(1.dp, MinSecondary)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("WEIGHT", color = MinGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(weightStr, fontSize = 18.sp, fontWeight = FontWeight.Black, color = MinAccent)
                        Text("KG", modifier = Modifier.padding(start = 2.dp, bottom = 2.dp), fontSize = 10.sp, color = MinGray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }

    if (selectedSession != null) {
        ModalBottomSheet(onDismissRequest = { selectedSession = null }, containerColor = MinSurface) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState())) {
                val date = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date(selectedSession!!.date))
                Text(date.uppercase(), color = MinBlue, fontWeight = FontWeight.Black, fontSize = 12.sp)
                Text("WORKOUT DETAILS", fontWeight = FontWeight.Black, fontSize = 24.sp, color = MinAccent)
                Spacer(modifier = Modifier.height(24.dp))
                
                val grouped = sessionRecords.groupBy { it.exerciseName }
                grouped.forEach { (name, recs) ->
                    Column(modifier = Modifier.padding(bottom = 24.dp)) {
                        Text(name, fontWeight = FontWeight.Bold, color = MinAccent, fontSize = 18.sp)
                        recs.forEachIndexed { i, r ->
                            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${i+1}", modifier = Modifier.width(24.dp), color = MinGray, fontWeight = FontWeight.Bold)
                                Text("${r.weight} kg x ${r.reps}", color = MinAccent)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun CalendarGrid(sessions: List<WorkoutSession>, onSessionClick: (WorkoutSession) -> Unit) {
    val calendar = Calendar.getInstance()
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
    
    val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                Text(day, color = MinGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        var dayCounter = 1
        for (row in 0..5) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0..6) {
                    val index = row * 7 + col
                    if (index >= firstDayOfWeek && dayCounter <= daysInMonth) {
                        val d = dayCounter
                        val sessionOnThisDay = sessions.find { 
                            val sCal = Calendar.getInstance().apply { timeInMillis = it.date }
                            sCal.get(Calendar.DAY_OF_MONTH) == d && 
                            sCal.get(Calendar.MONTH) == Calendar.getInstance().get(Calendar.MONTH)
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(if (sessionOnThisDay != null) MinBlue else if (d == currentDay) MinSecondary else Color.Transparent)
                                .clickable(enabled = sessionOnThisDay != null) { onSessionClick(sessionOnThisDay!!) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$d", color = if(sessionOnThisDay != null) MinAccent else if(d == currentDay) MinAccent else MinGray, fontSize = 12.sp, fontWeight = if(sessionOnThisDay != null) FontWeight.Black else FontWeight.Normal)
                        }
                        dayCounter++
                    } else {
                        Spacer(modifier = Modifier.size(32.dp))
                    }
                }
            }
            if (dayCounter > daysInMonth) break
        }
    }
}

@Composable
fun TrainedMusclesList(records: List<ExerciseRecord>, dao: WorkoutDao) {
    val muscleGroups = mapOf(
        "Chest" to listOf("Bench Press", "Dips"),
        "Back" to listOf("Pull Up", "Barbell Row", "Lat Pulldown"),
        "Legs" to listOf("Squat", "Leg Press"),
        "Shoulders" to listOf("Overhead Press"),
        "Arms" to listOf("Bicep Curl", "Dips")
    )
    
    val trainedGroups = muscleGroups.filter { group -> records.any { it.exerciseName in group.value } }.keys
    
    if (trainedGroups.isEmpty()) {
        Text("No data for last 7 days", color = MinGray, fontSize = 12.sp)
    } else {
        trainedGroups.forEach { group ->
            var isPR by remember { mutableStateOf(false) }
            LaunchedEffect(records) {
                val groupExercises = muscleGroups[group] ?: emptyList()
                for (ex in groupExercises) {
                    val recent = records.filter { it.exerciseName == ex }
                    if (recent.isNotEmpty()) {
                        val maxWeight = dao.getExercisePR(ex)
                        if (recent.any { it.weight.toFloatOrNull() ?: 0f >= (maxWeight ?: 0f) }) {
                            isPR = true; break
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MinBlue))
                Spacer(modifier = Modifier.width(8.dp))
                Text(group, color = MinAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (isPR) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.EmojiEvents, null, tint = MinGold, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
fun MuscleHumanoid(records: List<ExerciseRecord>) {
    val muscleGroups = mapOf(
        "Chest" to listOf("Bench Press", "Dips"),
        "Back" to listOf("Pull Up", "Barbell Row", "Lat Pulldown"),
        "Legs" to listOf("Squat", "Leg Press"),
        "Shoulders" to listOf("Overhead Press"),
        "Arms" to listOf("Bicep Curl", "Dips")
    )
    
    fun isTrained(name: String) = records.any { it.exerciseName in (muscleGroups[name] ?: emptyList()) }

    Box(modifier = Modifier.size(100.dp, 120.dp)) {
        Box(modifier = Modifier.size(16.dp).align(Alignment.TopCenter).clip(CircleShape).background(MinSecondary))
        Box(modifier = Modifier.size(32.dp, 40.dp).padding(top = 18.dp).align(Alignment.TopCenter).background(if(isTrained("Chest")) MinBlue else MinSecondary, RoundedCornerShape(4.dp)))
        Box(modifier = Modifier.size(10.dp, 40.dp).padding(top = 18.dp).align(Alignment.TopStart).offset(x = 24.dp).background(if(isTrained("Arms")) MinBlue else MinSecondary, RoundedCornerShape(2.dp)))
        Box(modifier = Modifier.size(10.dp, 40.dp).padding(top = 18.dp).align(Alignment.TopEnd).offset(x = (-24).dp).background(if(isTrained("Arms")) MinBlue else MinSecondary, RoundedCornerShape(2.dp)))
        Box(modifier = Modifier.size(12.dp, 45.dp).align(Alignment.BottomCenter).offset(x = (-8).dp).background(if(isTrained("Legs")) MinBlue else MinSecondary, RoundedCornerShape(2.dp)))
        Box(modifier = Modifier.size(12.dp, 45.dp).align(Alignment.BottomCenter).offset(x = 8.dp).background(if(isTrained("Legs")) MinBlue else MinSecondary, RoundedCornerShape(2.dp)))
    }
}

@Composable
fun BadgesScreen(sessionCount: Int) {
    Column(modifier = Modifier.fillMaxSize().background(MinBackground).padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("YOUR BADGES", fontWeight = FontWeight.Black, fontSize = 28.sp, color = MinAccent, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(32.dp))
        
        val badges = listOf(
            BadgeInfo("NOWICJUSZ", "Pierwszy krok za Tobą!", 1, Icons.Default.Star),
            BadgeInfo("DZIK", "Prawdziwy dzik siłowni!", 20, Icons.Default.Pets),
            BadgeInfo("KOKS", "Siła rośnie z każdym dniem!", 50, Icons.Default.FitnessCenter),
            BadgeInfo("LEGENDA", "Twoja forma przejdzie do historii!", 100, Icons.Default.MilitaryTech),
            BadgeInfo("MARATOŃCZYK", "Nigdy się nie poddajesz!", 200, Icons.AutoMirrored.Filled.DirectionsRun)
        )

        badges.forEach { badge ->
            val unlocked = sessionCount >= badge.target
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MinSurface),
                border = BorderStroke(1.dp, if(unlocked) MinGold.copy(0.3f) else MinSecondary)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(64.dp),
                        color = if(unlocked) MinGold.copy(alpha = 0.1f) else MinSecondary,
                        shape = CircleShape
                    ) {
                        Icon(badge.icon, null, tint = if(unlocked) MinGold else MinGray, modifier = Modifier.padding(16.dp))
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(badge.name, fontWeight = FontWeight.Black, color = if(unlocked) MinAccent else MinGray, fontSize = 16.sp)
                        Text(badge.desc, fontSize = 12.sp, color = MinGray)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "badge ${badge.name.lowercase()} ${sessionCount}/${badge.target}", 
                            fontSize = 10.sp, 
                            color = if(unlocked) MinGold else MinBlue, 
                            fontWeight = FontWeight.Bold
                        )
                        if (!unlocked) {
                            LinearProgressIndicator(
                                progress = { sessionCount.toFloat() / badge.target },
                                modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(4.dp),
                                color = MinBlue,
                                trackColor = MinSecondary
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

data class BadgeInfo(val name: String, val desc: String, val target: Int, val icon: ImageVector)

@Composable
fun NewsScreen() {
    Column(modifier = Modifier.fillMaxSize().background(MinBackground).padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("NEWS", fontWeight = FontWeight.Black, fontSize = 28.sp, color = MinAccent, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(32.dp))
        
        NEWS_DATA.forEach { news ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MinSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    AsyncImage(
                        model = news.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(news.date, color = MinBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(news.title, color = MinAccent, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun LoginMenu(onProfileSaved: (String, String, String, String, String, String, String) -> Unit) {
    var nick by remember { mutableStateOf("") }; var gender by remember { mutableStateOf("") }
    var h by remember { mutableStateOf("") }; var w by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }; var bio by remember { mutableStateOf("") }
    var imgUri by remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { imgUri = it }

    Scaffold(containerColor = MinBackground) { p ->
        Column(modifier = Modifier.padding(p).fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(modifier = Modifier.height(40.dp))
            Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(MinSecondary).clickable { launcher.launch("image/*") }) {
                if (imgUri != null) AsyncImage(model = imgUri, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.AddAPhoto, null, modifier = Modifier.align(Alignment.Center), tint = MinGray)
            }
            MinTextField(nick, { nick = it }, "Nickname")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text("MAN", color = if(gender=="Chłop") MinAccent else MinGray, modifier = Modifier.clickable { gender="Chłop" }, fontWeight = FontWeight.Bold)
                Text("WOMAN", color = if(gender=="Baba") MinAccent else MinGray, modifier = Modifier.clickable { gender="Baba" }, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MinTextField(h, { h = it }, "Height", Modifier.weight(1f), KeyboardType.Number)
                MinTextField(w, { w = it }, "Weight", Modifier.weight(1f), KeyboardType.Number)
            }
            MinTextField(age, { age = it }, "Age", keyboardType = KeyboardType.Number)
            Button(onClick = { onProfileSaved(nick, gender, h, w, age, bio, imgUri?.toString() ?: "") }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MinAccent, contentColor = MinBackground), shape = RoundedCornerShape(8.dp)) {
                Text("BEGIN", fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(userNick: String, userGender: String, userHeight: String, userWeight: String, userAge: String, userBio: String, userImageUri: String, onBack: () -> Unit, onUpdate: (String, String, String, String, String, String, String) -> Unit, onDeleteAccount: () -> Unit) {
    var nick by remember { mutableStateOf(userNick) }; var gender by remember { mutableStateOf(userGender) }
    var h by remember { mutableStateOf(userHeight) }; var w by remember { mutableStateOf(userWeight) }
    var age by remember { mutableStateOf(userAge) }; var bio by remember { mutableStateOf(userBio) }
    var imgUri by remember { mutableStateOf(userImageUri) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) imgUri = it.toString() }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MinSurface,
            title = { Text("DELETE ACCOUNT", color = MinRed, fontWeight = FontWeight.Black) },
            text = { Text("Are you sure you want to delete your account? All data will be lost forever.", color = MinAccent) },
            confirmButton = {
                TextButton(onClick = { 
                    showDeleteDialog = false
                    onDeleteAccount() 
                }) {
                    Text("DELETE", color = MinRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("CANCEL", color = MinGray)
                }
            }
        )
    }

    Scaffold(
        containerColor = MinBackground,
        topBar = {
            TopAppBar(
                title = { Text("PROFILE", fontWeight = FontWeight.Black, letterSpacing = 2.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { 
                    Text("SAVE", color = MinBlue, fontWeight = FontWeight.Black, modifier = Modifier.padding(end = 16.dp).clickable { onUpdate(nick, gender, h, w, age, bio, imgUri) }) 
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MinBackground, titleContentColor = MinAccent, navigationIconContentColor = MinAccent)
            )
        }
    ) { p ->
        Column(modifier = Modifier.padding(p).fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(MinSecondary).clickable { launcher.launch("image/*") }) {
                if (imgUri.isNotEmpty()) AsyncImage(model = imgUri, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Person, null, modifier = Modifier.align(Alignment.Center).size(48.dp), tint = MinGray)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            BasicTextField(
                value = nick,
                onValueChange = { nick = it },
                textStyle = TextStyle(color = MinAccent, fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(MinAccent),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text("BIO", color = MinGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))
                BasicTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    textStyle = TextStyle(color = MinAccent, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(MinAccent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (bio.isEmpty()) Text("Tell us about yourself...", color = MinSecondary, fontSize = 16.sp)
                        innerTextField()
                    }
                )
                HorizontalDivider(color = MinSecondary, thickness = 1.dp, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text("MAN", color = if(gender=="Chłop") MinAccent else MinGray, modifier = Modifier.clickable { gender="Chłop" }, fontWeight = FontWeight.Bold)
                Text("WOMAN", color = if(gender=="Baba") MinAccent else MinGray, modifier = Modifier.clickable { gender="Baba" }, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MinTextField(h, { h = it }, "Height", Modifier.weight(1f), KeyboardType.Number)
                MinTextField(w, { w = it }, "Weight", Modifier.weight(1f), KeyboardType.Number)
            }
            Spacer(modifier = Modifier.height(24.dp))
            
            MinTextField(age, { age = it }, "Age", keyboardType = KeyboardType.Number)
            
            Spacer(modifier = Modifier.weight(1f))
            Text("DELETE ACCOUNT", color = MinRed, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(vertical = 40.dp).clickable { showDeleteDialog = true })
        }
    }
}

val AVAILABLE_EXERCISES = listOf("Bench Press", "Squat", "Deadlift", "Overhead Press", "Pull Up", "Barbell Row", "Dips", "Bicep Curl", "Leg Press", "Lat Pulldown")

@Composable
fun WorkoutScreen(nick: String, img: String, onOpenProfile: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getDatabase(context).workoutDao() }
    var isWorking by remember { mutableStateOf(false) }
    var timer by remember { mutableLongStateOf(0L) }
    var sessionId by remember { mutableStateOf<Int?>(null) }
    val records = remember { mutableStateListOf<ExerciseRecord>() }

    LaunchedEffect(isWorking) {
        if(isWorking) { timer = 0; while(isWorking) { delay(1000); timer++ } }
    }

    Scaffold(containerColor = MinBackground) { p ->
        Column(modifier = Modifier.padding(p).fillMaxSize().padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(nick.uppercase(), fontSize = 12.sp, color = MinGray, letterSpacing = 1.sp)
                }
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MinSecondary).clickable { onOpenProfile() }) {
                    if (img.isNotEmpty()) AsyncImage(model = img, null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Default.Person, null, tint = MinGray, modifier = Modifier.padding(8.dp))
                }
            }

            if (!isWorking) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("START SESSION", modifier = Modifier.clickable {
                        isWorking = true
                        CoroutineScope(Dispatchers.IO).launch { sessionId = dao.insertSession(WorkoutSession()).toInt() }
                    }, fontWeight = FontWeight.Black, fontSize = 32.sp, letterSpacing = 4.sp, color = MinBlue)
                }
            } else {
                ActiveSessionView(timer, records, { records.add(it) }, { i, r -> records[i] = r }, {
                    val sid = sessionId; val recs = records.toList(); val dur = timer
                    isWorking = false; records.clear()
                    if(sid != null) CoroutineScope(Dispatchers.IO).launch {
                        dao.updateSession(WorkoutSession(sid, durationSeconds = dur))
                        recs.forEach { dao.insertRecord(it.copy(sessionId = sid)) }
                    }
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionView(timer: Long, records: SnapshotStateList<ExerciseRecord>, onAdd: (ExerciseRecord) -> Unit, onUpdate: (Int, ExerciseRecord) -> Unit, onFinish: () -> Unit) {
    var showList by remember { mutableStateOf(false) }
    val workoutExercises = remember { mutableStateListOf<String>() }

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(String.format("%02d:%02d", timer / 60, timer % 60), fontSize = 32.sp, fontWeight = FontWeight.Black, color = MinAccent)
            Text("FINISH", color = MinAccent, fontWeight = FontWeight.Black, modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(MinGreen).padding(horizontal = 12.dp, vertical = 6.dp).clickable { onFinish() })
        }
        Spacer(modifier = Modifier.height(24.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            workoutExercises.forEach { name ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(name, fontWeight = FontWeight.Black, fontSize = 20.sp, color = MinBlue, modifier = Modifier.padding(bottom = 12.dp))
                    
                    // Table Header
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("SET", modifier = Modifier.width(40.dp), color = MinGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("KG", modifier = Modifier.width(60.dp), color = MinGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("REPS", modifier = Modifier.width(60.dp), color = MinGray, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    val recs = records.mapIndexed { i, r -> i to r }.filter { it.second.exerciseName == name }
                    recs.forEachIndexed { idx, (origIdx, r) ->
                        val isSetDone = r.isDone
                        val rowBg = if (isSetDone) MinGreen.copy(alpha = 0.2f) else Color.Transparent
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), 
                            verticalAlignment = Alignment.CenterVertically, 
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("${idx+1}", modifier = Modifier.width(40.dp), color = if(isSetDone) MinGreen else MinAccent, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            
                            Box(modifier = Modifier.width(60.dp).clip(RoundedCornerShape(4.dp)).background(if(isSetDone) MinGreen.copy(0.2f) else MinSecondary).padding(vertical = 8.dp)) {
                                BasicTextField(
                                    value = r.weight, 
                                    onValueChange = { onUpdate(origIdx, r.copy(weight = it)) }, 
                                    textStyle = TextStyle(color = MinAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), 
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    cursorBrush = SolidColor(MinBlue)
                                )
                            }
                            
                            Box(modifier = Modifier.width(60.dp).clip(RoundedCornerShape(4.dp)).background(if(isSetDone) MinGreen.copy(0.2f) else MinSecondary).padding(vertical = 8.dp)) {
                                BasicTextField(
                                    value = r.reps, 
                                    onValueChange = { onUpdate(origIdx, r.copy(reps = it)) }, 
                                    textStyle = TextStyle(color = MinAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), 
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    cursorBrush = SolidColor(MinBlue)
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)).background(if(isSetDone) MinGreen else MinSecondary).clickable { onUpdate(origIdx, r.copy(isDone = !isSetDone)) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, tint = if(isSetDone) MinAccent else MinGray, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                    
                    Text(
                        "+ ADD SET", 
                        color = MinBlue, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp).clickable { 
                            val lastWeight = recs.lastOrNull()?.second?.weight ?: ""
                            val lastReps = recs.lastOrNull()?.second?.reps ?: ""
                            onAdd(ExerciseRecord(0, 0, name, weight = lastWeight, reps = lastReps)) 
                        }
                    )
                }
            }
            Button(
                onClick = { showList = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MinBlue.copy(0.1f), contentColor = MinBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ADD EXERCISE", fontWeight = FontWeight.Black)
            }
        }
    }

    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }, containerColor = MinSurface) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp)) {
                Text("ADD EXERCISE", modifier = Modifier.padding(24.dp), fontWeight = FontWeight.Black, fontSize = 18.sp, color = MinAccent)
                AVAILABLE_EXERCISES.forEach { ex ->
                    Text(ex, modifier = Modifier.fillMaxWidth().clickable { 
                        workoutExercises.add(ex)
                        onAdd(ExerciseRecord(0, 0, ex)) 
                        showList = false 
                    }.padding(horizontal = 24.dp, vertical = 16.dp), color = MinAccent, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
