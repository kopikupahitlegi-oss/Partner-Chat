package com.example.notifications

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.util.Log

enum class ToneCategory {
    CHAT,
    CALL
}

enum class ToneSource {
    BUILTIN,
    SYSTEM_DEFAULT,
    SYSTEM_URI,
    SILENT
}

data class ToneOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val source: ToneSource,
    val uriString: String? = null
)

object ToneCatalog {
    private const val TAG = "ToneCatalog"

    const val SILENT_ID = "silent"
    const val SYSTEM_DEFAULT_CHAT_ID = "system_default_chat"
    const val SYSTEM_DEFAULT_CALL_ID = "system_default_call"
    const val URI_PREFIX = "uri:"

    // Curated built-in chat notification tones (synthesized high-clarity chimes)
    const val BUILTIN_CHAT_EMERALD = "builtin:chat_emerald"
    const val BUILTIN_CHAT_CRYSTAL = "builtin:chat_crystal"
    const val BUILTIN_CHAT_BUBBLE = "builtin:chat_bubble"
    const val BUILTIN_CHAT_AURORA = "builtin:chat_aurora"
    const val BUILTIN_CHAT_PULSE = "builtin:chat_pulse"

    // Curated built-in incoming call ringtones (synthesized melodic loops)
    const val BUILTIN_CALL_CLASSIC = "builtin:call_classic"
    const val BUILTIN_CALL_HARMONY = "builtin:call_harmony"
    const val BUILTIN_CALL_HORIZON = "builtin:call_horizon"
    const val BUILTIN_CALL_CRYSTAL = "builtin:call_crystal"
    const val BUILTIN_CALL_PULSE = "builtin:call_pulse"

    const val DEFAULT_CHAT_TONE_ID = BUILTIN_CHAT_EMERALD
    const val DEFAULT_CHAT_TONE_TITLE = "Nada Partner"
    const val DEFAULT_CALL_TONE_ID = BUILTIN_CALL_CLASSIC
    const val DEFAULT_CALL_TONE_TITLE = "Dering Klasik"

    val builtInChatTones: List<ToneOption> = listOf(
        ToneOption(
            id = BUILTIN_CHAT_EMERALD,
            title = "Nada Partner",
            subtitle = "Harmoni tiga nada hangat khas Partner Bank",
            source = ToneSource.BUILTIN
        ),
        ToneOption(
            id = BUILTIN_CHAT_CRYSTAL,
            title = "Kristal Chime",
            subtitle = "Dentingan lonceng kristal jernih",
            source = ToneSource.BUILTIN
        ),
        ToneOption(
            id = BUILTIN_CHAT_BUBBLE,
            title = "Tetes Air",
            subtitle = "Efek gelembung lembut dan modern",
            source = ToneSource.BUILTIN
        ),
        ToneOption(
            id = BUILTIN_CHAT_AURORA,
            title = "Aurora Lembut",
            subtitle = "Akor lembut yang tenang dan elegan",
            source = ToneSource.BUILTIN
        ),
        ToneOption(
            id = BUILTIN_CHAT_PULSE,
            title = "Kilat Singkat",
            subtitle = "Dua ketukan cepat dan tegas",
            source = ToneSource.BUILTIN
        )
    )

    val builtInCallTones: List<ToneOption> = listOf(
        ToneOption(
            id = BUILTIN_CALL_CLASSIC,
            title = "Dering Klasik",
            subtitle = "Melodi arpeggio hangat dan mudah dikenali",
            source = ToneSource.BUILTIN
        ),
        ToneOption(
            id = BUILTIN_CALL_HARMONY,
            title = "Harmoni Modern",
            subtitle = "Rangkaian nada piano elektrik yang elegan",
            source = ToneSource.BUILTIN
        ),
        ToneOption(
            id = BUILTIN_CALL_HORIZON,
            title = "Cakrawala",
            subtitle = "Irama marimba ceria untuk panggilan masuk",
            source = ToneSource.BUILTIN
        ),
        ToneOption(
            id = BUILTIN_CALL_CRYSTAL,
            title = "Lonceng Kristal",
            subtitle = "Lonceng berderet terang dan bersih",
            source = ToneSource.BUILTIN
        ),
        ToneOption(
            id = BUILTIN_CALL_PULSE,
            title = "Ritme Dinamis",
            subtitle = "Nada panggilan berirama energik",
            source = ToneSource.BUILTIN
        )
    )

    fun silentOption(): ToneOption = ToneOption(
        id = SILENT_ID,
        title = "Senyap",
        subtitle = "Tanpa suara nada (hanya tampilan notifikasi)",
        source = ToneSource.SILENT
    )

    fun systemDefaultOption(category: ToneCategory): ToneOption = when (category) {
        ToneCategory.CHAT -> ToneOption(
            id = SYSTEM_DEFAULT_CHAT_ID,
            title = "Bawaan Sistem",
            subtitle = "Gunakan nada notifikasi standar perangkat",
            source = ToneSource.SYSTEM_DEFAULT,
            uriString = Settings.System.DEFAULT_NOTIFICATION_URI?.toString()
        )
        ToneCategory.CALL -> ToneOption(
            id = SYSTEM_DEFAULT_CALL_ID,
            title = "Bawaan Sistem",
            subtitle = "Gunakan nada dering telepon standar perangkat",
            source = ToneSource.SYSTEM_DEFAULT,
            uriString = Settings.System.DEFAULT_RINGTONE_URI?.toString()
        )
    }

    fun builtInOptionsFor(category: ToneCategory): List<ToneOption> = when (category) {
        ToneCategory.CHAT -> builtInChatTones
        ToneCategory.CALL -> builtInCallTones
    }

    fun loadSystemTones(context: Context, category: ToneCategory, limit: Int = 24): List<ToneOption> {
        val result = mutableListOf<ToneOption>()
        result += systemDefaultOption(category)
        result += silentOption()

        runCatching {
            val manager = RingtoneManager(context).apply {
                setType(
                    if (category == ToneCategory.CHAT) {
                        RingtoneManager.TYPE_NOTIFICATION
                    } else {
                        RingtoneManager.TYPE_RINGTONE
                    }
                )
            }
            val cursor = manager.cursor
            var count = 0
            val seenTitles = mutableSetOf<String>()
            while (cursor.moveToNext() && count < limit) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                    ?.trim()
                    .orEmpty()
                val uri = runCatching { manager.getRingtoneUri(cursor.position) }.getOrNull()
                if (title.isNotEmpty() && uri != null && seenTitles.add(title.lowercase())) {
                    result += ToneOption(
                        id = "$URI_PREFIX$uri",
                        title = title,
                        subtitle = if (category == ToneCategory.CHAT) "Nada notifikasi sistem" else "Nada dering sistem",
                        source = ToneSource.SYSTEM_URI,
                        uriString = uri.toString()
                    )
                    count++
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Unable to enumerate system ringtones: ${err.message}")
        }
        return result
    }

    fun uriToToneId(uri: Uri?): String {
        if (uri == null) return SILENT_ID
        return "$URI_PREFIX$uri"
    }

    fun resolveUriTitle(context: Context, uri: Uri?, fallback: String): String {
        if (uri == null) return "Senyap"
        return runCatching {
            RingtoneManager.getRingtone(context, uri)?.getTitle(context)?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: fallback
    }

    fun resolveRawResId(toneId: String): Int? = when (toneId) {
        BUILTIN_CHAT_EMERALD -> com.example.R.raw.tone_chat_emerald
        BUILTIN_CHAT_CRYSTAL -> com.example.R.raw.tone_chat_crystal
        BUILTIN_CHAT_BUBBLE -> com.example.R.raw.tone_chat_bubble
        BUILTIN_CHAT_AURORA -> com.example.R.raw.tone_chat_aurora
        BUILTIN_CHAT_PULSE -> com.example.R.raw.tone_chat_pulse
        BUILTIN_CALL_CLASSIC -> com.example.R.raw.tone_call_classic
        BUILTIN_CALL_HARMONY -> com.example.R.raw.tone_call_harmony
        BUILTIN_CALL_HORIZON -> com.example.R.raw.tone_call_horizon
        BUILTIN_CALL_CRYSTAL -> com.example.R.raw.tone_call_crystal
        BUILTIN_CALL_PULSE -> com.example.R.raw.tone_call_pulse
        else -> null
    }

    fun resolvePlayableUri(context: Context, toneId: String, category: ToneCategory): Uri? {
        if (isSilent(toneId)) return null
        val rawId = resolveRawResId(toneId)
        if (rawId != null) {
            return Uri.parse("android.resource://${context.packageName}/$rawId")
        }
        return resolveSystemUri(toneId, category)
    }

    fun resolveSystemUri(toneId: String, category: ToneCategory): Uri? = when {
        toneId == SILENT_ID -> null
        toneId == SYSTEM_DEFAULT_CHAT_ID -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: Settings.System.DEFAULT_NOTIFICATION_URI
        toneId == SYSTEM_DEFAULT_CALL_ID -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: Settings.System.DEFAULT_RINGTONE_URI
        toneId.startsWith(URI_PREFIX) -> runCatching {
            Uri.parse(toneId.removePrefix(URI_PREFIX))
        }.getOrNull()
        else -> null
    }

    fun isBuiltIn(toneId: String): Boolean = toneId.startsWith("builtin:")

    fun isSilent(toneId: String): Boolean = toneId == SILENT_ID
}
