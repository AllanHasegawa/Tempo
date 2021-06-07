/*
 * Copyright 2017 Allan Yoshio Hasegawa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.tempo.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.tempo.Tempo
import io.tempo.TempoEvent
import io.tempo.TempoEventsListener
import io.tempo.sample.databinding.ActivityMainBinding
import io.tempo.sample.databinding.ItemTempoEventBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private var eventsRvAdapter: EventsRvAdapter? = null

    private var scope: CoroutineScope? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        binding.mainEventsRv.layoutManager = LinearLayoutManager(this)
        eventsRvAdapter = EventsRvAdapter()
        binding.mainEventsRv.adapter = eventsRvAdapter!!

        binding.mainStartSyncingBt.setOnClickListener {
            try {
                Tempo.start()
            } catch (t: Throwable) {
                Toast.makeText(this, t.toString(), Toast.LENGTH_SHORT).show()
                t.printStackTrace()
            }
        }
        binding.mainStopSyncingBt.setOnClickListener { Tempo.stop() }
        binding.mainTriggerManualSyncBt.setOnClickListener { Tempo.triggerManualSyncNow() }

        scope = CoroutineScope(Dispatchers.Main).apply {
            launch {
                async {
                    delay(10_000L) // simulates late initialization
                    Tempo.now().collect { now ->
                        Log.d("TempoEvent", "This is now: ${formatTimestamp(now)}")
                    }
                }

                while (isActive) {
                    val systemTime = System.currentTimeMillis()
                    val tempoTime = Tempo.nowOrNull()
                    val systemTimeFormatted = formatTimestamp(systemTime)
                    val tempoTimeFormatted = tempoTime?.let { formatTimestamp(it) } ?: "-"

                    binding.mainTempoInitializedTv.text = Tempo.isInitialized().toString()
                    binding.mainSystemTimeFormattedTv.text = systemTimeFormatted
                    binding.mainTempoNowFormattedTv.text = tempoTimeFormatted
                    binding.mainTempoActiveTsTv.text = Tempo.activeTimeSourceId()

                    delay(20L)
                }
            }
        }

        Tempo.addEventsListener(listener)

        if (!hasGPSPermission()) {
            ActivityCompat.requestPermissions(
                this,
                listOf(Manifest.permission.ACCESS_FINE_LOCATION).toTypedArray(), 42
            )
        }
    }

    override fun onDestroy() {
        Tempo.removeEventsListener(listener)
        eventsRvAdapter = null
        binding.mainEventsRv.adapter = null
        scope?.cancel()
        scope = null
        super.onDestroy()
    }

    private fun hasGPSPermission(): Boolean {

        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val listener = TempoEventsListener { event -> eventsRvAdapter?.addEvent(event) }
}

class EventsRvAdapter : RecyclerView.Adapter<EventsRvAdapter.VH>() {
    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val binding = ItemTempoEventBinding.bind(itemView)

        fun bind(event: TempoEvent) {
            with(binding) {
                itemTempoTimeTv.text = formatTimestamp(event.systemTime)
                itemTempoEventTv.text = formatTempoEvent(event)
            }
        }
    }

    private val events = mutableListOf<TempoEvent>()

    fun addEvent(event: TempoEvent) {
        events.add(event)
        notifyItemInserted(events.size - 1)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        events.getOrNull(position)?.let { event ->
            holder.bind(event)
        }
    }

    override fun getItemCount(): Int = events.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.item_tempo_event, parent, false)
        return VH(view)
    }
}

private fun formatTimestamp(time: Long): String {
    val format = SimpleDateFormat("yyyy/MM/dd    HH:mm:ss:SSSS", Locale.ENGLISH)
    return format.format(Date(time))
}

private fun formatTempoEvent(event: TempoEvent): String =
    when (event) {
        is TempoEvent.Initializing -> "Initializing"
        is TempoEvent.Initialized -> "Initialized"
        is TempoEvent.TSSyncRequest -> {
            val tsId = event.timeSource.config.id
            "TSSyncRequest($tsId)"
        }
        is TempoEvent.TSSyncSuccess -> {
            val tsId = event.timeSourceId
            "TSSyncSuccess($tsId)"
        }
        is TempoEvent.TSSyncFailure -> {
            Log.e("Tempo", "Error:", event.error)
            val tsId = event.timeSource.config.id
            val errorMsg = event.error.message
            "TSSyncFailure($tsId, $errorMsg)"
        }
        is TempoEvent.SyncStart -> "SyncStart"
        is TempoEvent.SyncSuccess -> "SyncSuccess"
        is TempoEvent.SyncFail -> "SyncFail"
        is TempoEvent.CacheRestored -> "CacheRestored(${event.cache.timeSourceId})"
        is TempoEvent.CacheSaved -> "CacheSaved(${event.cache.timeSourceId})"
        is TempoEvent.SchedulerSetupSkip -> "SchedulerSetupSkip"
        is TempoEvent.SchedulerSetupStart -> "SchedulerSetupStart"
        is TempoEvent.SchedulerSetupComplete -> "SchedulerSetupComplete"
        is TempoEvent.SchedulerSetupFailure -> "SchedulerSetupFailure(${event.error.localizedMessage})"
    }
