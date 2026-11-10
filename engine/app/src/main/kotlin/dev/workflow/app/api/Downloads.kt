package dev.workflow.app.api

import dev.workflow.app.domain.ExportFile
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/** An export as a JSON attachment named for what it holds and the day (PORTABLE.md section 4). */
fun download(file: ExportFile): ResponseEntity<Any> = ResponseEntity.ok()
    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.name).build().toString())
    .contentType(MediaType.APPLICATION_JSON)
    .body(file.body)
