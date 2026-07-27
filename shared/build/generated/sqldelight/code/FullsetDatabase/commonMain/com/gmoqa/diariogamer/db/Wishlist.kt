package com.gmoqa.diariogamer.db

import kotlin.Long
import kotlin.String

public data class Wishlist(
  public val id: Long,
  public val platform: String,
  public val game: String,
  public val slug: String,
  public val cover_url: String,
  public val added_at: Long,
)
