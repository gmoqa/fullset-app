package com.gmoqa.diariogamer.db

import kotlin.Long
import kotlin.String

public data class Notes(
  public val id: Long,
  public val game_id: Long,
  public val text: String,
  public val created_at: Long,
  public val audio_path: String,
  public val duration_ms: Long,
)
