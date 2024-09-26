package no.nav.familie.ef.mottak.api

import no.nav.familie.ef.mottak.service.PdfService
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api/spireTest"], produces = [MediaType.APPLICATION_JSON_VALUE])
class SpireTestController(
    private val pdfService: PdfService,
) {
    @GetMapping("skrivSistePdfTilFil")
    @Unprotected
    fun skrivSistePdfTilFil(): String = pdfService.skrivSistePdfTilFil()

}
