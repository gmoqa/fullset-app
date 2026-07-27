package com.gmoqa.diariogamer.db

import kotlin.Long
import kotlin.String

public data class Photos(
  public val id: Long,
  public val game_id: Long,
  public val path: String,
  public val caption: String,
  public val created_at: Long,
)
