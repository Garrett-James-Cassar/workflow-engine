package dev.workflow.app.clients

import dev.workflow.app.telemetry.EndpointContext
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.stereotype.Component

private const val CALLER_HEADER = "X-Fleet-Caller"
private const val THIS_APP = "workflow-engine"

/** Sends this app as `X-Fleet-Caller` (the callee's audit log records it as `via`) and the origin on every outbound call in the W3C `baggage` header, next to `traceparent`. */
@Component
class OriginPropagation : RestClientCustomizer {
    override fun customize(builder: org.springframework.web.client.RestClient.Builder) {
        builder.requestInterceptor(ClientHttpRequestInterceptor { request, body, execution ->
            stamp(request.headers)
            execution.execute(request, body)
        })
    }

    private fun stamp(headers: org.springframework.http.HttpHeaders) {
        headers.set(CALLER_HEADER, THIS_APP)
        EndpointContext.origin()?.let { headers.set(EndpointContext.BAGGAGE, EndpointContext.baggageWith(headers.getFirst(EndpointContext.BAGGAGE), it)) }
    }
}
