package no.nav.familie.ef.mottak.service

import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.familie.ef.mottak.config.DOKUMENTTYPE_BARNETILSYN
import no.nav.familie.ef.mottak.config.DOKUMENTTYPE_OVERGANGSSTØNAD
import no.nav.familie.ef.mottak.config.DOKUMENTTYPE_SKOLEPENGER
import no.nav.familie.ef.mottak.encryption.EncryptedString
import no.nav.familie.ef.mottak.integration.PdfClient
import no.nav.familie.ef.mottak.integration.SpirePdfClient
import no.nav.familie.ef.mottak.repository.EttersendingRepository
import no.nav.familie.ef.mottak.repository.SøknadRepository
import no.nav.familie.ef.mottak.repository.VedleggRepository
import no.nav.familie.ef.mottak.repository.domain.EncryptedFile
import no.nav.familie.ef.mottak.repository.domain.Søknad
import no.nav.familie.ef.mottak.service.Testdata.vedlegg
import no.nav.familie.kontrakter.felles.objectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull

internal class PdfServiceTest {
    private val søknadRepository: SøknadRepository = mockk()
    private val vedleggRepository: VedleggRepository = mockk()
    private val ettersendingRepository: EttersendingRepository = mockk()
    private val pdfClient: PdfClient = mockk()
    private val spirePdfClient: SpirePdfClient = mockk()
    private val pdfService: PdfService = PdfService(søknadRepository, ettersendingRepository, vedleggRepository, pdfClient, spirePdfClient)

    private val pdf = EncryptedFile("321".toByteArray())
    private val søknadOvergangsstønadId = "søknadOvergangsstønadId"
    private val søknadOvergangsstønad =
        Søknad(
            id = søknadOvergangsstønadId,
            søknadJson = createValidSøknadJson(Testdata.søknadOvergangsstønad),
            søknadPdf = null,
            fnr = "654",
            dokumenttype = DOKUMENTTYPE_OVERGANGSSTØNAD,
            journalpostId = null,
            saksnummer = null,
        )

    private val søknadSkolepengerId = "søknadSkolepengerId"
    private val søknadSkolepenger =
        Søknad(
            id = søknadSkolepengerId,
            søknadJson = createValidSøknadJson(Testdata.søknadSkolepenger),
            søknadPdf = null,
            fnr = "654",
            dokumenttype = DOKUMENTTYPE_SKOLEPENGER,
            journalpostId = null,
            saksnummer = null,
        )

    private val søknadBarnetilsynId = "søknadBarnetilsynId"
    private val søknadBarnetilsyn =
        Søknad(
            id = søknadBarnetilsynId,
            søknadJson = createValidSøknadJson(Testdata.søknadBarnetilsyn),
            søknadPdf = null,
            fnr = "654",
            dokumenttype = DOKUMENTTYPE_BARNETILSYN,
            journalpostId = null,
            saksnummer = null,
        )

    @BeforeEach
    fun setUp() {
        søknadsRepositoryVilReturnere(søknadOvergangsstønad, søknadBarnetilsyn, søknadSkolepenger)
        every {
            vedleggRepository.finnTitlerForSøknadId(any())
        } returns vedlegg.map { it.tittel }
        pdfClientVilReturnere(pdf)
        every { spirePdfClient.lagFeltMapPdf(any()) } answers { byteArrayOf(51,50,49) }
    }

    @Test
    fun `Søknad skal oppdateres med pdf når pdf genereres`() {
        // Given
        val slot = slot<Søknad>()
        capturePdfAddedToSøknad(slot)
        // When
        pdfService.lagPdf(søknadOvergangsstønadId)
        // Then
        assertThat(pdf).isEqualTo(slot.captured.søknadPdf)
    }

    @Test
    fun `Skolepengesøknad skal oppdateres med pdf når pdf genereres`() {
        // Given
        val slot = slot<Søknad>()
        capturePdfAddedToSøknad(slot)
        // When
        pdfService.lagPdf(søknadSkolepengerId)
        // Then
        assertThat(pdf).isEqualTo(slot.captured.søknadPdf)
    }

    @Test
    fun `Søknad med pdf skal lagres`() {
        // Given
        val slot = slot<Søknad>()
        capturePdfAddedToSøknad(slot)
        // When
        pdfService.lagPdf(søknadBarnetilsynId)
        // Then
        verify(exactly = 1) { vedleggRepository.finnTitlerForSøknadId(any()) }
        verify(exactly = 1) {
            søknadRepository.update(slot.captured)
        }
    }

    private fun pdfClientVilReturnere(pdf: EncryptedFile) {
        every {
            pdfClient.lagPdf(any())
        } returns pdf.bytes
    }

    private fun søknadsRepositoryVilReturnere(vararg søknad: Søknad) {
        søknad.forEach {
            every {
                søknadRepository.findByIdOrNull(it.id)
            } returns it
        }
    }

    private fun createValidSøknadJson(søknad: Any): EncryptedString = EncryptedString(objectMapper.writeValueAsString(søknad))

    private fun capturePdfAddedToSøknad(slot: CapturingSlot<Søknad>) {
        every {
            søknadRepository.update(capture(slot))
        } answers {
            slot.captured
        }
    }
}
