package no.nav.familie.ef.mottak.task

import no.nav.familie.ef.mottak.repository.SoknadRepository
import no.nav.familie.ef.mottak.repository.domain.Soknad
import no.nav.familie.ef.mottak.service.DateTimeService
import no.nav.familie.ef.mottak.service.SakService
import no.nav.familie.prosessering.AsyncTaskStep
import no.nav.familie.prosessering.TaskStepBeskrivelse
import no.nav.familie.prosessering.domene.Task
import no.nav.familie.prosessering.domene.TaskRepository
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Infotrygd er vanligvis stengt mellom 21 og 6, men ikke alltid.
 * Hvis tasken feiler i denne tida så lager den en ny task og kjører den kl 06
 */
@Service
@TaskStepBeskrivelse(taskStepType = OpprettSakTask.TYPE,
                     beskrivelse = "Oppretter sak i Infotrygd")
class OpprettSakTask(private val taskRepository: TaskRepository,
                     private val sakService: SakService,
                     private val dateTimeService: DateTimeService,
                     private val soknadRepository: SoknadRepository) : AsyncTaskStep {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override fun doTask(task: Task) {
        val oppgaveId: String = task.metadata[LagBehandleSakOppgaveTask.behandleSakOppgaveIdKey] as String?
                                ?: error("Fant ikke oppgaveId for behandle-sak-oppgave i tasken")
        try {
            val sakId = sakService.opprettSak(task.payload, oppgaveId)?.trim()
            val soknad = soknadRepository.findByIdOrNull(task.payload) ?: error("Søknad har forsvunnet!")
            val soknadMedSaksnummer = soknad.copy(saksnummer = sakId)
            soknadRepository.save(soknadMedSaksnummer)
            opprettNesteTask(task, soknadMedSaksnummer)
        } catch (e: Exception) {
            if (erKlokkenMellom21Og06()) {
                logger.info("Oppretter en ny task som kjører kl 06 id=${task.id} callId=${task.callId}")
                taskRepository.save(Task(payload= task.payload, type = task.type, metadata = task.metadata, triggerTid = kl06IdagEllerNesteDag()))
            } else {
                throw e
            }
        }
    }

    //Ikke endre til onCompletion
    private fun opprettNesteTask(task: Task, soknad: Soknad) {
        val nesteTask = if (soknad?.saksnummer != null) {
            Task(OppdaterBehandleSakOppgaveTask.TYPE, task.payload, task.metadata)
        } else {
            logger.warn("Det er allerede opprettet en sak for denne oppgaven - trolig gjort manuelt av saksbehandler")
            Task(HentSaksnummerFraJoarkTask.HENT_SAKSNUMMER_FRA_JOARK, task.payload, task.metadata)
        }
        taskRepository.save(nesteTask)
    }

    private fun erKlokkenMellom21Og06(): Boolean {
        val localTime = dateTimeService.now().toLocalTime()
        return localTime.isAfter(LocalTime.of(21, 0)) || localTime.isBefore(LocalTime.of(6, 0))
    }

    private fun kl06IdagEllerNesteDag(): LocalDateTime {
        val now = dateTimeService.now()
        return if (now.toLocalTime().isBefore(LocalTime.of(6, 0))) {
            now.toLocalDate().atTime(6, 0)
        } else {
            now.toLocalDate().plusDays(1).atTime(6, 0)
        }
    }

    companion object {

        const val TYPE = "opprettSak"
    }
}
