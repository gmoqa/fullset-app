package com.gmoqa.diariogamer.db

import app.cash.sqldelight.Transacter
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.gmoqa.diariogamer.db.shared.newInstance
import com.gmoqa.diariogamer.db.shared.schema
import kotlin.Unit

public interface FullsetDatabase : Transacter {
  public val fullsetQueries: FullsetQueries

  public companion object {
    public val Schema: SqlSchema<QueryResult.Value<Unit>>
      get() = FullsetDatabase::class.schema

    public operator fun invoke(driver: SqlDriver): FullsetDatabase =
        FullsetDatabase::class.newInstance(driver)
  }
}
