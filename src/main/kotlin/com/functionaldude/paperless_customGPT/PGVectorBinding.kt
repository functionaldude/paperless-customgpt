package com.functionaldude.paperless_customGPT

import org.jooq.*
import org.jooq.impl.AbstractBinding
import org.jooq.impl.DSL
import org.postgresql.util.PGobject
import java.sql.PreparedStatement
import java.sql.SQLFeatureNotSupportedException
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
      value = v.toPgVectorLiteral()
    }

    // jOOQ will not try to infer a datatype now: we bind at JDBC level
    stmt.setObject(i, pg)
  }

  /*

  override fun sql(ctx: BindingSQLContext<FloatArray>) {
    ctx.render().visit(DSL.`val`(ctx.convert(converter()).value())).sql("::vector")
  }

  override fun register(ctx: BindingRegisterContext<FloatArray>) {
    ctx.statement().registerOutParameter(ctx.index(), Types.ARRAY)
  }

  override fun get(ctx: BindingGetStatementContext<FloatArray>) {
    val statement = ctx.statement()
    val vectorAsString = statement.getString(ctx.index())
    ctx.value(converter().from(vectorAsString))
  }

  // the below methods aren't needed in Postgres:

  override fun get(ctx: BindingGetSQLInputContext<FloatArray>?) {
    throw SQLFeatureNotSupportedException()
  }

  override fun set(ctx: BindingSetSQLOutputContext<FloatArray>?) {
    throw SQLFeatureNotSupportedException()
  }

   */
}