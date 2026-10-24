package com.example.mymusicapplication

import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mymusicapplication.ui.theme.MyMusicApplicationTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyMusicApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MusicPlayerScreen(
                        modifier = Modifier.padding(innerPadding),
                        activity = this
                    )
                }
            }
        }
    }

    fun playMusic() {
        val player = mediaPlayer
        if (player == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.test)?.apply {
                isLooping = true
                start() }
        } else {
            if (!player.isPlaying && player.currentPosition >= player.duration - 100) {
                player.seekTo(0) // 播完了，回到开头
            }
            if (!player.isPlaying) {
                player.start()
            }
        }
    }

    fun pauseMusic() {
        mediaPlayer?.let { if (it.isPlaying) it.pause() }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
    }

    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            0
        }
    }

    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

// 歌词数据类
data class LyricLine(val timeMs: Long, val text: String)

@Composable
fun MusicPlayerScreen(modifier: Modifier = Modifier, activity: MainActivity) {
    val lyrics = remember {
        parseLrc(activity.resources.openRawResource(R.raw.test_lyric).bufferedReader().readText())
    }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableLongStateOf(1L) }

    // 防止拖动进度条时，UI 和轮询打架
    var isDragging by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val currentLineIndex = lyrics.indexOfLast { it.timeMs <= currentPosition }

    // 轮询更新
    LaunchedEffect(Unit) {
        while (true) {
            if (!isDragging) {
                currentPosition = activity.getCurrentPosition().toLong()
            }
            isPlaying = activity.isPlaying()
            duration = activity.getDuration().toLong()
            delay(100)
        }
    }

    // 高亮行改变时平滑滚动
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            // 把当前行滚动到屏幕中间位置（保留了你之前调的 -600 偏移）
            listState.animateScrollToItem(index = currentLineIndex, scrollOffset = -600)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ================= 新增：顶部标题区 =================
        Spacer(modifier = Modifier.height(40.dp)) // 顶部留点白，避免贴到状态栏
        Text(
            text = "夏日尽头的我们", // 这里换成你的歌名
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        // ====================================================

        // 歌词显示区域
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 80.dp),
            verticalArrangement = Arrangement.Center
        ) {
            itemsIndexed(lyrics) { index, lyric ->
                val isHighlighted = index == currentLineIndex
                Text(
                    text = lyric.text,
                    fontSize = if (isHighlighted) 20.sp else 16.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    color = if (isHighlighted) MaterialTheme.colorScheme.primary else Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clickable {
                            // 点击歌词跳转
                            activity.seekTo(lyric.timeMs.toInt())
                            currentPosition = lyric.timeMs

                            // 点击后主动滚到那一行
                            coroutineScope.launch {
                                listState.animateScrollToItem(index = index, scrollOffset = -400)
                            }
                        }
                )
            }
        }

        // 底部控制区
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                if (isPlaying) activity.pauseMusic() else activity.playMusic()
            }) {
                Text(if (isPlaying) "暂停" else "播放")
            }

            Spacer(modifier = Modifier.width(16.dp))

            Slider(
                value = currentPosition.toFloat(),
                onValueChange = { newValue ->
                    isDragging = true
                    currentPosition = newValue.toLong()
                },
                onValueChangeFinished = {
                    isDragging = false
                    activity.seekTo(currentPosition.toInt())
                },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// LRC 解析函数
fun parseLrc(lrcText: String): List<LyricLine> {
    val lines = mutableListOf<LyricLine>()
    val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

    lrcText.lines().forEach { line ->
        val match = regex.find(line)
        if (match != null) {
            val (min, sec, msStr, text) = match.destructured
            val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
            val timeMs = min.toLong() * 60000 + sec.toLong() * 1000 + ms
            if (text.isNotBlank()) {
                lines.add(LyricLine(timeMs, text.trim()))
            }
        }
    }
    return lines.sortedBy { it.timeMs }
}