package com.dev.anhnd.waveformviewbar

import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.dev.anhnd.waveformviewbar.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val binding = DataBindingUtil.inflate<ActivityMainBinding>(LayoutInflater.from(this), R.layout.activity_main, null, false)

        binding.waveformView.setAudioPath("/storage/self/primary/VideoCompress/Extract/toidayeuemlauroi.mp3")
    }
}