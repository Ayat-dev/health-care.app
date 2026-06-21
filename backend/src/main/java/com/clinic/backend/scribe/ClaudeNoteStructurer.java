package com.clinic.backend.scribe;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.clinic.backend.dto.ConsultationDraftDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Implémentation Claude du {@link ClinicalNoteStructurer} (P4.1). Utilise la
 * sortie structurée (schéma JSON dérivé de {@link ConsultationDraftDto}) : le
 * modèle est contraint de renvoyer un JSON validable, mappé 1:1 sur les champs
 * du dossier — pas de parsing fragile.
 *
 * <p>Le client est injecté en {@link ObjectProvider} : absent lorsque le scribe
 * est désactivé (cf. {@link ScribeConfig}), auquel cas {@link ScribeService}
 * court-circuite avant d'atteindre cette classe.
 */
@Slf4j
@Component
public class ClaudeNoteStructurer implements ClinicalNoteStructurer {

    private static final String SYSTEM_PROMPT = """
        Tu es un assistant de scribe médical pour une clinique. À partir de la \
        transcription brute d'une consultation (parole du médecin et/ou du \
        patient, possiblement bruitée), tu produis une note clinique structurée \
        EN FRANÇAIS au format SOAP, en remplissant uniquement les champs demandés.

        Règles strictes :
        - N'invente JAMAIS d'information absente de la transcription.
        - Si un champ n'est pas évoqué, renvoie une chaîne vide.
        - Pour les constantes vitales, ne reporte une valeur que si elle est \
          explicitement énoncée, sous forme de nombre seul (ex: '37.2', '120', \
          '80'), sinon chaîne vide. N'extrapole jamais une constante.
        - Propose des codes CIM-10 dans icd10Codes uniquement si le diagnostic \
          est clair, séparés par des virgules ; sinon chaîne vide.
        - Reste fidèle au vocabulaire médical et au contenu réellement dit.
        - Tu ne poses pas de diagnostic définitif à la place du médecin : tu \
          structures ce qui a été exprimé. Le médecin relit et valide.
        """;

    private final ObjectProvider<AnthropicClient> clientProvider;
    private final String model;

    public ClaudeNoteStructurer(ObjectProvider<AnthropicClient> clientProvider,
                                @Value("${app.scribe.model:claude-opus-4-8}") String model) {
        this.clientProvider = clientProvider;
        this.model = model;
    }

    @Override
    public ConsultationDraftDto structure(String transcript) {
        AnthropicClient client = clientProvider.getIfAvailable();
        if (client == null) {
            throw new IllegalStateException("Scribe IA non configuré (clé API Anthropic absente).");
        }

        StructuredMessageCreateParams<ConsultationDraftDto> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4096L)
                .system(SYSTEM_PROMPT)
                .addUserMessage(transcript)
                .outputConfig(ConsultationDraftDto.class)
                .build();

        try {
            return client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(structured -> structured.text())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Réponse du scribe IA vide."));
        } catch (RuntimeException e) {
            log.error("Échec de la structuration scribe IA", e);
            throw new IllegalStateException("Le scribe IA est momentanément indisponible.", e);
        }
    }
}
