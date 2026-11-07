package dev.workflow.app.config.web

import jakarta.servlet.FilterChain
import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper

private val WITH_BODY = setOf("POST", "PUT", "PATCH")

/**
 * First stop for a request body (SECURITY.md): a body over the cap (1 MB by default) answers 413, and a body that is
 * not JSON answers 415. A declared length over the cap is refused before anything is read; a chunked body is
 * counted as it is read and fails the read.
 */
@Component
class BodyGuardFilter(
    private val json: JsonMapper,
    @Value("\${workflow.limits.max-body-bytes:1048576}") private val maxBytes: Long,
) : OncePerRequestFilter(), Ordered {
    private val log = LoggerFactory.getLogger(BodyGuardFilter::class.java)

    override fun getOrder() = Ordered.HIGHEST_PRECEDENCE + 10

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val verdict = verdict(request)
        if (verdict == null) return chain.doFilter(CappedRequest(request, maxBytes), response)
        log.warn("Rejected request: {}", verdict.detail)
        response.writeProblem(json, verdict)
    }

    private fun verdict(request: HttpServletRequest) = when {
        request.method !in WITH_BODY || !hasBody(request) -> null
        request.contentLengthLong > maxBytes -> problem(413, ProblemType.TOO_LARGE, "Payload too large", "The request body is over $maxBytes bytes")
        !isJson(request.contentType) -> problem(415, ProblemType.UNSUPPORTED_MEDIA, "Unsupported media type", "The body must be application/json")
        else -> null
    }

    private fun hasBody(request: HttpServletRequest) = request.contentLengthLong > 0 || request.getHeader("Transfer-Encoding") != null

    private fun isJson(contentType: String?): Boolean = try {
        contentType != null && MediaType.parseMediaType(contentType).let { it.isCompatibleWith(MediaType.APPLICATION_JSON) || it.subtype.endsWith("+json") }
    } catch (_: InvalidMediaTypeException) {
        false
    }
}

private class CappedRequest(request: HttpServletRequest, private val max: Long) : HttpServletRequestWrapper(request) {
    override fun getInputStream(): ServletInputStream = Capped(super.getInputStream(), max)
}

private class Capped(private val source: ServletInputStream, private val max: Long) : ServletInputStream() {
    private var seen = 0L

    override fun read(): Int = source.read().also { if (it >= 0) count(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = source.read(b, off, len).also { if (it > 0) count(it) }

    private fun count(n: Int) {
        seen += n
        if (seen > max) throw PayloadTooLargeException()
    }

    override fun isFinished() = source.isFinished

    override fun isReady() = source.isReady

    override fun setReadListener(listener: ReadListener?) = source.setReadListener(listener)
}
