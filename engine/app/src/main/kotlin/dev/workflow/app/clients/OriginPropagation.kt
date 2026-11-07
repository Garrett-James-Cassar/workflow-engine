package dev.workflow.app.clients

import dev.workflow.app.telemetry.EndpointContext
import org.springframework.boot.restclient.RestClientCustomizer
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.stereotype.Component

/** Sends the origin on every outbound call in the W3C `baggage` header, next to `traceparent`. */
@Component
class OriginPropagation : RestClientCustomizer {
    override fun customize(builder: org.springframework.web.client.RestClient.Builder) {
        builder.requestInterceptor(ClientHttpRequestInterceptor { request, body, execution ->
            EndpointContext.origin()?.let { request.headers.set(EndpointContext.BAGGAGE, EndpointContext.baggageWith(request.headers.getFirst(EndpointContext.BAGGAGE), it)) }
            execution.execute(request, body)
        })
    }
}
