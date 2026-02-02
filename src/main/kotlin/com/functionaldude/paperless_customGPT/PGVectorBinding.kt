package com.functionaldude.paperless_customGPT

import org.jooq.BindingGetResultSetContext
import org.jooq.BindingSetStatementContext
import org.jooq.Converter
import org.jooq.impl.AbstractBinding
import org.postgresql.util.PGobject
import java.sql.PreparedStatement
import java.sql.Types

@Suppress("UNCHECKED_CAST")
class PGVectorBinding : AbstractBinding<Any, FloatArray>() {

  override fun converter(): Converter<Any, FloatArray> {
    return object : Converter<Any, FloatArray> {
      override fun from(databaseObject: Any?): FloatArray {
        return when (databaseObject) {
          null -> floatArrayOf()
          is FloatArray -> databaseObject
          else -> databaseObject.toString().removeSurrounding("[", "]").split(",").map { it.toFloat() }.toFloatArray()
        }
      }

      override fun to(userObject: FloatArray): Any {
        return userObject.toString()
      }

      override fun fromType(): Class<Any> = Any::class.java

      override fun toType(): Class<FloatArray> = List::class.java as Class<FloatArray>
    }
  }

  override fun get(ctx: BindingGetResultSetContext<FloatArray>) {
    val resultSet = ctx.resultSet()
    val vectorAsString = resultSet.getString(ctx.index())
    ctx.value(converter().from(vectorAsString))
  }

  override fun set(ctx: BindingSetStatementContext<FloatArray>) {
    val stmt: PreparedStatement = ctx.statement()
    val i = ctx.index()
    val v = ctx.value()

    if (v == null) {
      stmt.setNull(i, Types.OTHER)
      return
    }

    val pg = PGobject().apply {
      type = "vector"
      value = toPgVectorLiteral(v)
    }

    // jOOQ will not try to infer a datatype now: we bind at JDBC level
    stmt.setObject(i, pg)
  }

  private fun toPgVectorLiteral(vector: FloatArray): String {
    return vector.joinToString(prefix = "[", postfix = "]") { it.toString() }
  }
}
