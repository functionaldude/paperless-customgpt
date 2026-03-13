package com.functionaldude.paperless_customGPT.agent

object AgentOperationText {
  const val DOCUMENTS_TAG_DESCRIPTION =
    "Paperless document browsing endpoints for the agent to retrieve raw document data."
  const val LIST_DOCUMENTS_SUMMARY = "List available documents"
  const val LIST_DOCUMENTS_DESCRIPTION =
    "Returns every Paperless PDF document together with metadata and extracted content."
  const val LIST_DOCUMENTS_RESPONSE_DESCRIPTION = "Documents successfully retrieved."
  const val FIND_DOCUMENT_BY_ID_SUMMARY = "Fetch a document by id"
  const val FIND_DOCUMENT_BY_ID_DESCRIPTION =
    "Looks up the Paperless document for the supplied identifier and returns its metadata and content."
  const val FIND_DOCUMENT_BY_ID_RESPONSE_DESCRIPTION = "Document was found and returned."
  const val DOCUMENT_ID_DESCRIPTION = "Numeric Paperless document id."
  const val INVALID_DOCUMENT_ID_RESPONSE_DESCRIPTION = "The supplied id was not numeric."
  const val DOCUMENT_NOT_FOUND_RESPONSE_DESCRIPTION = "No document exists for the requested id."
  const val INVALID_DOCUMENT_ID_MESSAGE = "Document id must be a number"
  const val DOCUMENT_NOT_FOUND_MESSAGE = "Document not found"

  const val RAG_TAG_DESCRIPTION =
    "Retrieval augmented generation APIs that offer semantic search across Paperless documents."
  const val RAG_QUERY_REQUEST_SCHEMA_DESCRIPTION = "Parameters for a RAG similarity search."
  const val RAG_QUERY_DESCRIPTION =
    "Natural language prompt used to search previously ingested Paperless documents."
  const val RAG_QUERY_EXAMPLE = "What is the renewal premium for my car insurance?"
  const val RAG_TOP_K_DESCRIPTION = "Optional number of top results to return. Values over 20 are clamped."
  const val RAG_SEARCH_SUMMARY = "Run a semantic search"
  const val RAG_SEARCH_DESCRIPTION =
    "Uses pgvector similarity search to retrieve the most relevant Paperless documents for the provided question."
  const val RAG_SEARCH_REQUEST_BODY_DESCRIPTION =
    "Search parameters including the natural language query and optional limit for the number of hits."
  const val RAG_SEARCH_RESPONSE_DESCRIPTION = "Search results were computed successfully."
  const val RAG_BLANK_QUERY_RESPONSE_DESCRIPTION = "Query text was blank."
  const val RAG_BLANK_QUERY_MESSAGE = "Query must not be blank"
}
