package app.aurio.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Other apps' own icons, as installed on this phone (the same ones the launcher shows). Read
 * through the package manager with the launcher query Aurio already declares: no extra
 * permission, nothing stored, nothing leaves the phone. Missing apps fall back to [AppTile]'s
 * initial.
 */
object AppIcons {
    private val cache = LruCache<String, ImageBitmap>(96)
    private val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun key(pkg: String, px: Int) = "$pkg@$px"

    fun cached(pkg: String, px: Int): ImageBitmap? = cache.get(key(pkg, px))

    /** Loads and caches [pkg]'s icon at [px] square, or null if the app is not installed. */
    fun load(context: Context, pkg: String, px: Int): ImageBitmap? {
        cached(pkg, px)?.let { return it }
        if (pkg in missing) return null
        return try {
            context.packageManager.getApplicationIcon(pkg).toBitmap(px, px).asImageBitmap()
                .also { cache.put(key(pkg, px), it) }
        } catch (_: PackageManager.NameNotFoundException) {
            missing += pkg
            null
        }
    }
}

/** [pkg]'s icon at [size], loaded off the main thread; null until loaded or if unavailable. */
@Composable
fun rememberAppIcon(pkg: String?, size: Dp): ImageBitmap? {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    var icon by remember(pkg, px) { mutableStateOf(pkg?.let { AppIcons.cached(it, px) }) }
    LaunchedEffect(pkg, px) {
        if (pkg != null && icon == null) icon = withContext(Dispatchers.IO) { AppIcons.load(context, pkg, px) }
    }
    return icon
}
