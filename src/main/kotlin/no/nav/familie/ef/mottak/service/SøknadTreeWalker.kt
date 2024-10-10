package no.nav.familie.ef.mottak.service

import no.nav.familie.ef.mottak.repository.domain.Ettersending
import no.nav.familie.kontrakter.ef.søknad.*
import no.nav.familie.kontrakter.felles.Fødselsnummer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor

object SøknadTreeWalker {
    private val endNodes =
        setOf<KClass<*>>(
            String::class,
            Int::class,
            Boolean::class,
            Double::class,
            Dokumentasjon::class,
            Fødselsnummer::class,
            MånedÅrPeriode::class,
            Datoperiode::class,
            Adresse::class,
            LocalDate::class,
            LocalDateTime::class,
            Month::class,
            Long::class,
        )

    fun mapOvergangsstønad(
        søknad: SøknadOvergangsstønad,
        vedleggTitler: List<String>,
    ): Map<String, Any> {
        val finnFelter = finnFelter(søknad)
        val vedlegg = feltlisteMap("Vedlegg", listOf(Feltformaterer.mapVedlegg(vedleggTitler)))
        return feltlisteMap("Søknad om overgangsstønad (NAV 15-00.01)", finnFelter + vedlegg)
    }

    fun mapBarnetilsyn(
        søknad: SøknadBarnetilsyn,
        vedleggTitler: List<String>,
    ): Map<String, Any> {
        val finnFelter = finnFelter(søknad)
        val vedlegg = feltlisteMap("Vedlegg", listOf(Feltformaterer.mapVedlegg(vedleggTitler)))
        return feltlisteMap("Søknad om stønad til barnetilsyn (NAV 15-00.02)", finnFelter + vedlegg)
    }

    fun mapSkolepenger(
        søknad: SøknadSkolepenger,
        vedleggTitler: List<String>,
    ): Map<String, Any> {
        val finnFelter = finnFelter(søknad)
        val vedlegg = feltlisteMap("Vedlegg", listOf(Feltformaterer.mapVedlegg(vedleggTitler)))
        return feltlisteMap("Søknad om stønad til skolepenger (NAV 15-00.04)", finnFelter + vedlegg)
    }

    fun mapSkjemafelter(skjema: SkjemaForArbeidssøker): Map<String, Any> {
        val finnFelter = finnFelter(skjema)
        return feltlisteMap("Skjema for arbeidssøker - 15-08.01", finnFelter)
    }

    fun mapEttersending(
        ettersending: Ettersending,
        vedleggTitler: List<String>,
    ): Map<String, Any> {
        val infoMap =
            feltlisteMap(
                "Ettersending av vedlegg",
                listOf(
                    Feltformaterer.feltMap("Stønadstype", ettersending.stønadType),
                    Feltformaterer.feltMap("Fødselsnummer", ettersending.fnr),
                    Feltformaterer.feltMap(
                        "Dato mottatt",
                        ettersending.opprettetTid.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")),
                    ),
                ),
            )
        val vedleggMap = feltlisteMap("Dokumenter vedlagt", listOf(Feltformaterer.mapVedlegg(vedleggTitler)))
        return feltlisteMap("Ettersending", listOf(infoMap, vedleggMap))
    }

    private fun finnFelter(entitet: Any): List<Map<String, *>> {
        // Det går ikke å hente elementene i en liste med reflection, så vi traverserer den som vanlig.
        if (entitet is List<Any?>) {
            return entitet
                .filterNotNull()
                .map { finnFelter(it) }
                .flatten()
        }
        val parametere = konstruktørparametere(entitet)

        val list =
            parametere
                .asSequence()
                .map { finnSøknadsfelt(entitet, it) }
                .filter { it.visibility == KVisibility.PUBLIC }
                .mapNotNull { getFeltverdi(it, entitet) }
                .map { finnFelter(it) } // Kall rekursivt videre
                .flatten()
                .toList()

        if (entitet is Søknadsfelt<*>) {
            if (entitet.verdi!! is Dokumentasjon) {
                @Suppress("UNCHECKED_CAST")
                return listOf(mapDokumentasjon(entitet as Søknadsfelt<Dokumentasjon>))
            }
            if (entitet.label == "Barna dine"){
                return listOf(feltlisteMap(entitet.label, list, "Tabel"))
            }
            if (entitet.verdi!!::class in endNodes) {
                return listOf(Feltformaterer.mapEndenodeTilUtskriftMap(entitet))
            }
            if (entitet.verdi is List<*>) {
                val verdiliste = entitet.verdi as List<*>
                if (verdiliste.isNotEmpty() && verdiliste.first() is String) {
                    return listOf(Feltformaterer.mapEndenodeTilUtskriftMap(entitet))
                }
            }
            return listOf(feltlisteMap(entitet.label, list))
        }
        return list
    }

    private fun mapDokumentasjon(entitet: Søknadsfelt<Dokumentasjon>): Map<String, *> = feltlisteMap(entitet.label, listOf(Feltformaterer.mapEndenodeTilUtskriftMap(entitet.verdi.harSendtInnTidligere)))

    private fun feltlisteMap(
        label: String,
        verdi: List<*>,
        type: String? = null
    ): Map<String, Any>  {
        return if (type == null){
            mapOf( "label" to label, "verdiliste" to verdi)
        } else  {
            mapOf( "label" to label, "type" to type, "verdiliste" to verdi)
        }
    }

    /**
     * Henter ut verdien for felt på entitet.
     */
    private fun getFeltverdi(
        felt: KProperty1<out Any, Any?>,
        entitet: Any,
    ) = felt.getter.call(entitet)

    /**
     * Finn første (og eneste) felt på entiteten som har samme navn som konstruktørparameter.
     */
    private fun finnSøknadsfelt(
        entity: Any,
        konstruktørparameter: KParameter,
    ) = entity::class.declaredMemberProperties.first { it.name == konstruktørparameter.name }

    /**
     * Konstruktørparametere er det eneste som gir oss en garantert rekkefølge for feltene, så vi henter disse først.
     */
    private fun konstruktørparametere(entity: Any) = entity::class.primaryConstructor?.parameters ?: emptyList()
}
