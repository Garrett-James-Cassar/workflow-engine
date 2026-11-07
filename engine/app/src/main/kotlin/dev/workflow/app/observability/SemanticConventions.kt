package dev.workflow.app.observability

import io.micrometer.common.KeyValue
import io.micrometer.common.KeyValues
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.observation.ClientRequestObservationContext
import org.springframework.http.client.observation.ClientRequestObservationConvention
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention
import org.springframework.http.server.observation.OpenTelemetryServerRequestObservationConvention
import org.springframework.http.server.observation.ServerRequestObservationConvention

private const val NONE = "none"

/**
 * RED metrics on the OpenTelemetry HTTP semantic conventions (OBSERVABILITY.md obs.4): the server timer is
 * `http.server.request.duration` with `http.request.method`, `http.response.status_code` and `http.route`;
 * the client timer is `http.client.request.duration` with the same method and status plus `server.address`.
 * Both are histograms in seconds (application.yaml), and their rate and error ratio derive from them.
 */
@Configuration(proxyBeanMethods = false)
class SemanticConventions {
    @Bean
    fun serverRequestConvention(): ServerRequestObservationConvention = OpenTelemetryServerRequestObservationConvention()

    @Bean
    fun clientRequestConvention(): ClientRequestObservationConvention = OtelClientRequestConvention()
}

class OtelClientRequestConvention : DefaultClientRequestObservationConvention("http.client.request.duration") {
    override fun getLowCardinalityKeyValues(context: ClientRequestObservationContext): KeyValues {
        val request = context.carrier
        val status = runCatching { context.response?.statusCode?.value() }.getOrNull()
        return KeyValues.of(
            KeyValue.of("http.request.method", request?.method?.name() ?: NONE),
            KeyValue.of("server.address", request?.uri?.host ?: NONE),
            KeyValue.of("http.response.status_code", status?.toString() ?: NONE),
            KeyValue.of("error.type", context.error?.javaClass?.simpleName ?: NONE),
        )
    }
}
