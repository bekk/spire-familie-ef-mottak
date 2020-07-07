package no.nav.familie.ef.mottak.integration

import no.nav.familie.ef.mottak.config.SakConfig
import no.nav.familie.http.client.AbstractRestClient
import no.nav.familie.http.client.MultipartBuilder
import no.nav.familie.kontrakter.ef.sak.SakRequest
import no.nav.familie.kontrakter.ef.sak.Skjemasak
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.client.RestOperations
import org.springframework.web.util.DefaultUriBuilderFactory

@Service
class SøknadClient(@Qualifier("restTemplateAzure") operations: RestOperations,
                   sakConfig: SakConfig) : AbstractRestClient(operations, "sak") {

    private val sendInnSakUri = DefaultUriBuilderFactory().uriString(sakConfig.url).path(PATH_SAK).build()

    private val sendInnSkjemasakUri = DefaultUriBuilderFactory().uriString(sakConfig.url).path(PATH_SKJEMASAK).build()

    fun sendTilSak(sak: SakRequest, vedlegg: Map<String, ByteArray>): HttpStatus? {
        log.info("Sender søknad til {}", sendInnSakUri)

        try {
            val multipartBuilder = MultipartBuilder().withJson("sak", sak)
            vedlegg.forEach { multipartBuilder.withByteArray("vedlegg", it.key, it.value) }
            return postForEntity(sendInnSakUri, multipartBuilder.build(), MultipartBuilder.MULTIPART_HEADERS)
        } catch (e: RestClientResponseException) {
            log.warn("Innsending til sak feilet. Responskode: {}, body: {}",
                     e.rawStatusCode,
                     e.responseBodyAsString)
            throw IllegalStateException("Innsending til sak feilet. Status: ${e.rawStatusCode}, body: ${e.responseBodyAsString}",
                                        e)
        } catch (e: RestClientException) {
            throw IllegalStateException("Innsending til sak feilet.", e)
        }

    }

    fun sendTilSak(skjemasak: Skjemasak): HttpStatus? {
        log.info("Sender søknad til {}", sendInnSkjemasakUri)

        try {
            return postForEntity(sendInnSkjemasakUri, skjemasak)
        } catch (e: RestClientResponseException) {
            log.warn("Innsending til sak feilet. Responskode: {}, body: {}",
                     e.rawStatusCode,
                     e.responseBodyAsString)
            throw IllegalStateException("Innsending til sak feilet. Status: ${e.rawStatusCode}, body: ${e.responseBodyAsString}",
                                        e)
        } catch (e: RestClientException) {
            throw IllegalStateException("Innsending til sak feilet.", e)
        }

    }

    companion object {
        const val PATH_SAK = "sak/sendInn"
        const val PATH_SKJEMASAK = "skjemasak/sendInn"
    }

}
