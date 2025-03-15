package io.recursio.livekitandroid

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
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

class MainActivity : AppCompatActivity() {
    lateinit var room: Room
    private lateinit var binding: ActivityMainBinding // name of the activity!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        room = LiveKit.create(applicationContext)

        binding.btnGetToken.setOnClickListener {
            var tc = TokenClient(
                "http://brick.recursio.io:3030/token",
                "room1",
                "user-identity-1",
                { token ->
                    token?.let {
                        val tkn = Gson().fromJson(it, Token::class.java)
                        updateUI(tkn)
                    }
                })
            tc.fetchToken().start()
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
            //binding.txvHelloWorld.text = token.token
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
                //val localParticipant = room.localParticipant
                //localParticipant.setCameraEnabled(true)
            }
        }
    }

    private fun onTrackSubscribed(event: RoomEvent.TrackSubscribed) {
        Log.i("MainActivity", "Track subscribed")
        val track = event.track
        if (track is VideoTrack && track.name == "room-tiny-0") {

            track.addRenderer(binding.renderer)
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