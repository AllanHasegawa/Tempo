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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.tempo.Tempo
import io.tempo.TempoEvent
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.item_tempo_event.view.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private val createSubscriptions = CompositeDisposable()
    private var eventsRvAdapter: EventsRvAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        main_events_rv.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this)
        eventsRvAdapter = EventsRvAdapter()
        main_events_rv.adapter = eventsRvAdapter

        Flowable.interval(0, 100, TimeUnit.MILLISECONDS)
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    val systemTime = System.currentTimeMillis()
                    val tempoTime = Tempo.now()
                    val systemTimeFormatted = formatTimestamp(systemTime)
                    val tempoTimeFormatted = tempoTime?.let { formatTimestamp(it) } ?: "-"

                    main_system_time_formatted_tv.text = systemTimeFormatted
                    main_tempo_now_formatted_tv.text = tempoTimeFormatted
                }
                .also { createSubscriptions.add(it) }


        Tempo.observeEvents()
                .onBackpressureBuffer()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::addEventToRv)
                .also { createSubscriptions.add(it) }


        if (!hasGPSPermission()) {
            ActivityCompat.requestPermissions(this,
                    listOf(Manifest.permission.ACCESS_FINE_LOCATION).toTypedArray(), 42)
        }
    }

    override fun onDestroy() {
        eventsRvAdapter = null
        main_events_rv.adapter = null
        createSubscriptions.clear()
        super.onDestroy()
    }

    private fun addEventToRv(event: TempoEvent) {
        eventsRvAdapter?.addEvent(event)
    }

    private fun hasGPSPermission(): Boolean {

        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

class EventsRvAdapter : androidx.recyclerview.widget.RecyclerView.Adapter<EventsRvAdapter.VH>() {
    class VH(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        fun bind(event: TempoEvent) {
            itemView.item_tempo_time_tv.text = formatTimestamp(event.systemTime)
            itemView.item_tempo_event_tv.text = formatTempoEvent(event)
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
                val tsId = event.timeSource.config().id
                "TSSyncRequest($tsId)"
            }
            is TempoEvent.TSSyncSuccess -> {
                val tsId = event.wrapper.timeSource.config().id
                val tsRequestTime = event.wrapper.cache.requestTime
                "TSSyncSuccess($tsId, $tsRequestTime)"
            }
            is TempoEvent.TSSyncFailure -> {
                Log.e("Tempo", "Error:", event.error)
                val tsId = event.timeSource.config().id
                val errorMsg = event.errorMsg
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
            is TempoEvent.SchedulerSetupFailure -> "SchedulerSetupFailure(${event.error?.localizedMessage})"
        }
