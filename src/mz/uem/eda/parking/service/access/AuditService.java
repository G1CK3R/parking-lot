package mz.uem.eda.parking.service.access;

import java.time.LocalDateTime;
import java.util.Objects;

import mz.uem.eda.parking.model.AuditEntry;
import mz.uem.eda.parking.structures.LinkedQueue;
import mz.uem.eda.parking.structures.Queue;

/**
 * O registo de auditoria (RF23), em memória: uma fila pela ordem do tempo,
 * que só cresce pelo fim. A {@code persistence} repõe-na no arranque; a
 * {@code ParkingDesk} junta uma linha depois de cada operação sensível, já
 * gravada.
 *
 * <p>Os filtros do ecrã de auditoria são uma busca sequencial: percorre-se a
 * fila uma vez e copiam-se as linhas que servem para uma fila nova, que já
 * fica pela ordem do tempo.
 */
public class AuditService {

    private final Queue<AuditEntry> entries = new LinkedQueue<>();

    /**
     * Junta uma linha ao fim do registo.
     *
     * @param entry a linha, já gravada
     * @complexity O(1)
     */
    public void add(AuditEntry entry) {
        entries.enqueue(Objects.requireNonNull(entry, "entry não pode ser null"));
    }

    /**
     * As linhas do período, de um utilizador e de uma operação.
     *
     * @param from o início, incluído
     * @param to o fim, excluído
     * @param username só deste utilizador, ou {@code null} para todos
     * @param action só desta operação, ou {@code null} para todas
     * @return as linhas, da mais antiga para a mais recente
     * @complexity O(N), uma passagem pelo registo inteiro
     */
    public Queue<AuditEntry> filter(LocalDateTime from, LocalDateTime to, String username,
            AuditEntry.Action action) {
        Objects.requireNonNull(from, "from não pode ser null");
        Objects.requireNonNull(to, "to não pode ser null");
        Queue<AuditEntry> result = new LinkedQueue<>();
        for (AuditEntry entry : entries) {
            boolean inPeriod = !entry.getAt().isBefore(from) && entry.getAt().isBefore(to);
            boolean ofUser = username == null || username.equals(entry.getUsername());
            boolean ofAction = action == null || action == entry.getAction();
            if (inPeriod && ofUser && ofAction) {
                result.enqueue(entry);
            }
        }
        return result;
    }

    /**
     * @return o registo inteiro, pela ordem do tempo
     * @complexity O(1) para obter; O(N) para percorrer
     */
    public Iterable<AuditEntry> entries() {
        return entries;
    }

    /**
     * @return quantas linhas tem o registo; O(1)
     * @complexity O(1)
     */
    public int size() {
        return entries.size();
    }
}
