package com.metrolist.music.spotify

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.metrolist.music.MainActivity
import com.metrolist.music.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SpotifyOAuthActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val progress = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(ProgressBar(this@SpotifyOAuthActivity))
            addView(TextView(this@SpotifyOAuthActivity).apply { setText(R.string.spotify_oauth_connecting) })
        }
        setContentView(progress)
        val model = ViewModelProvider(this)[AuthorizationModel::class.java]
        model.authorize(SpotifyAccount.get(this), intent?.data)
        // Keep callback credentials out of subsequent activity intents.
        intent?.data = null
        lifecycleScope.launch {
            val message = model.result.filterNotNull().first()
            Toast.makeText(this@SpotifyOAuthActivity, message, Toast.LENGTH_LONG).show()
            if (isTaskRoot) {
                startActivity(Intent(this@SpotifyOAuthActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            finish()
        }
    }

    class AuthorizationModel : ViewModel() {
        internal val result = MutableStateFlow<Int?>(null)
        private var started = false

        internal fun authorize(account: SpotifyAccount, uri: Uri?) {
            if (started) return
            started = true
            viewModelScope.launch {
                result.value = try {
                    if (uri == null) throw SpotifyImportException(SpotifyImportFailure.INVALID)
                    account.completeAuthorization(uri)
                    R.string.spotify_oauth_connected
                } catch (error: CancellationException) {
                    throw error
                } catch (error: SpotifyImportException) {
                    if (error.reason == SpotifyImportFailure.ACCESS_DENIED) R.string.spotify_oauth_denied
                    else R.string.spotify_oauth_failed
                } catch (_: Exception) {
                    R.string.spotify_oauth_failed
                }
            }
        }
    }
}
