package com.functionaldude.paperless_customGPT.documents

import com.functionaldude.paperless.jooq.public.tables.references.DOCUMENTS_TAG
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.util.UUID

class PaperlessTagServiceTest {

  @Test
  fun `all tags preserve multiple hierarchy levels and count only directly assigned active root documents`() =
    withTagDatabase { dsl, service ->
      dsl.execute(
        """
        insert into documents_tag (id, name, tn_parent_id) values
          (1, 'Finance', null),
          (2, 'Invoices', 1),
          (3, 'Utilities', 2),
          (4, 'Archived', null),
          (5, 'Personal', null),
          (6, 'Versions', null)
        """.trimIndent()
      )
      dsl.execute(
        """
        insert into documents_document (id, root_document_id, deleted_at) values
          (100, null, null),
          (101, null, null),
          (102, null, null),
          (103, null, timestamp '2026-01-01 00:00:00'),
          (104, 100, null)
        """.trimIndent()
      )
      dsl.execute(
        """
        insert into documents_document_tags (document_id, tag_id) values
          (100, 1), (100, 2), (101, 2), (102, 3),
          (103, 2), (103, 4), (104, 1), (104, 6)
        """.trimIndent()
      )

      assertThat(service.findAllTags()).containsExactly(
        TagDto(id = 4, name = "Archived", parentId = null, documentCount = 0),
        TagDto(id = 1, name = "Finance", parentId = null, documentCount = 1),
        TagDto(id = 2, name = "Invoices", parentId = 1, documentCount = 2),
        TagDto(id = 5, name = "Personal", parentId = null, documentCount = 0),
        TagDto(id = 3, name = "Utilities", parentId = 2, documentCount = 1),
        TagDto(id = 6, name = "Versions", parentId = null, documentCount = 0),
      )
    }

  @Test
  fun `all tags returns an empty list when no tags exist`() = withTagDatabase { _, service ->
    assertThat(service.findAllTags()).isEmpty()
  }

  @Test
  fun `all tags returns more than a document page of tags without truncating names`() = withTagDatabase { dsl, service ->
    for (id in 1..120) {
      dsl.insertInto(DOCUMENTS_TAG, DOCUMENTS_TAG.ID, DOCUMENTS_TAG.NAME)
        .values(id, "Tag $id / Überprüfung")
        .execute()
    }

    val tags = service.findAllTags()

    assertThat(tags).hasSize(120)
    assertThat(tags.map { it.name }).containsExactlyInAnyOrderElementsOf(
      (1..120).map { "Tag $it / Überprüfung" }
    )
    assertThat(tags).allSatisfy { tag ->
      assertThat(tag.parentId).isNull()
      assertThat(tag.documentCount).isZero()
    }
  }

  private fun withTagDatabase(test: (DSLContext, PaperlessDocumentService) -> Unit) {
    DriverManager.getConnection(
      "jdbc:h2:mem:tags-${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
    ).use { connection ->
      val dsl = DSL.using(connection, SQLDialect.POSTGRES)
      dsl.execute(
        """
        create table documents_tag (
          id integer primary key,
          name varchar(128) not null,
          tn_parent_id integer references documents_tag(id)
        )
        """.trimIndent()
      )
      dsl.execute(
        """
        create table documents_document (
          id integer primary key,
          root_document_id integer references documents_document(id),
          deleted_at timestamp with time zone
        )
        """.trimIndent()
      )
      dsl.execute(
        """
        create table documents_document_tags (
          document_id integer not null references documents_document(id),
          tag_id integer not null references documents_tag(id),
          unique(document_id, tag_id)
        )
        """.trimIndent()
      )
      test(dsl, PaperlessDocumentService(dsl, PaperlessUrlProvider("https://paperless.example")))
    }
  }
}
