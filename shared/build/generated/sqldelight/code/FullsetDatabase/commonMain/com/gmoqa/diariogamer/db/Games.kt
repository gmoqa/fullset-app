package com.gmoqa.diariogamer.db

import kotlin.Long
import kotlin.String

public data class Games(
  public val id: Long,
  public val name: String,
  public val platform: String,
  public val cover_url: String,
  public val cover_path: String,
  public val playing: Long,
  public val backlog: Long,
  public val created_at: Long,
  public val region: String,
  public val release_year: Long?,
  public val genre: String,
  public val condition: String,
  public val slug: String,
  public val publisher: String,
  public val serial: String,
  public val digital: Long,
)
