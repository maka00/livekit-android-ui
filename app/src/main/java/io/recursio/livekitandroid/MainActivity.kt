package io.recursio.livekitandroid

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.VideoTrack
import io.recursio.livekitandroid.databinding.ActivityMainBinding
import io.recursio.livekitandroid.model.Token
import kotlinx.coroutines.launch

import android.Manifest
import android.view.View
import android.widget.AdapterView
import io.recursio.livekitandroid.model.RoomTrack
import livekit.org.webrtc.RendererCommon
import io.livekit.android.room.track.LocalVideoTrack

class MainActivity : AppCompatActivity() {
    lateinit var room: Room
    private lateinit var binding: ActivityMainBinding // name of the activity!
    private lateinit var roomsAdapter: ArrayAdapter<String>
    private lateinit var rooms: MutableList<RoomTrack>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        room = LiveKit.create(applicationContext)
        requestNeededPermissions {}

        binding.btnGetToken.setOnClickListener {
                var tc = TokenClient(
                    "http://brick.recursio.io:3030/token",
                    "room1",
                    "android-identity-1",
                    { token ->
                        token?.let {
                            val tkn = Gson().fromJson(it, Token::class.java)
                            updateUI(tkn)
                        }
                    })
                tc.fetchToken().start()
            }

        rooms = mutableListOf()

        // link the adapter to the spinner dropdown
        roomsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item)
        roomsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.dropdown.adapter = roomsAdapter
        binding.dropdown.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View,
                position: Int,
                id: Long
            ) {
                onDropDownItemSelected(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Handle case where no item is selected if needed
            }
        }

        val view = binding.root
        setContentView(view)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun updateUI(token: Token) {
        runOnUiThread {
            Toast.makeText(this, "Token fetched!", Toast.LENGTH_SHORT).show()

            room.initVideoRenderer(binding.renderer)
            lifecycleScope.launch {
                room.connect("ws://brick.recursio.io:7880", token.token)

                launch {
                    room.events.collect { event ->
                        when (event) {
                            is RoomEvent.TrackSubscribed -> onTrackSubscribed(event)
                            else -> { /* ignore */
                            }
                        }
                    }
                }
                // to publish phone camera:
                // val localParticipant = room.localParticipant
                // localParticipant.name = "android-identity-1"
                // localParticipant.setCameraEnabled(true)
            }
        }
    }

    private fun onDropDownItemSelected(position: Int) {
        val roomTrack = rooms[position]
        Toast.makeText(this, roomTrack.name, Toast.LENGTH_SHORT).show()
        for (roomTrack in rooms) {
            var track = roomTrack.track as VideoTrack
            track.removeRenderer(binding.renderer)
        }
        var track = roomTrack.track as VideoTrack
        track.addRenderer(binding.renderer)
    }

    private fun onTrackSubscribed(event: RoomEvent.TrackSubscribed) {
        Log.i("MainActivity", "Track subscribed")
        val track = event.track
        if (track is VideoTrack) {
            this.rooms.add(RoomTrack(track.name, track))
            this.roomsAdapter.add(track.name)
        } else
            Log.i("MainActivity", "Ignoring track: ${track.name}")

    }

    private fun requestNeededPermissions(onHasPermissions: () -> Unit) {
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                var hasDenied = false
                // Check if any permissions weren't granted.
                for (grant in grants.entries) {
                    if (!grant.value) {
                        Toast.makeText(this, "Missing permission: ${grant.key}", Toast.LENGTH_SHORT)
                            .show()
                        hasDenied = true
                    }
                }

                if (!hasDenied) {
                    onHasPermissions()
                }
            }

        // Assemble the needed permissions to request
        val neededPermissions = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        ).filter { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED }
            .toTypedArray()

        if (neededPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(neededPermissions)
        } else {
            onHasPermissions()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        room.disconnect()
    }
}