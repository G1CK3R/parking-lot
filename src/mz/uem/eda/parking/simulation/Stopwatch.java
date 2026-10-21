package mz.uem.eda.parking.simulation;

/**
 * Cronómetro para as medições: quanto tempo real demora uma simulação ou um
 * algoritmo. Usa {@link System#nanoTime()}, que serve para medir intervalos
 * (não é a hora do relógio).
 *
 * <p>Uso: {@code start()}, o trabalho, {@code stop()}; depois
 * {@link #elapsedNanos()} ou {@link #elapsedMillis()}. Pode recomeçar com
 * outro {@code start()}.
 *
 * <p>Uma medição isolada em Java é ruidosa (o compilador JIT ainda está a
 * aquecer). Para o relatório, mede-se várias vezes e usa-se a mediana ou a
 * média das últimas.
 */
public class Stopwatch {

    private long startedAt;
    private long elapsed;
    private boolean running;

    /**
     * Começa a contar. Apaga a medição anterior.
     *
     * @throws IllegalStateException se já estiver a contar
     */
    public void start() {
        if (running) {
            throw new IllegalStateException("o cronómetro já está a contar");
        }
        elapsed = 0;
        running = true;
        startedAt = System.nanoTime();
    }

    /**
     * Pára de contar.
     *
     * @return o tempo medido, em nanossegundos
     * @throws IllegalStateException se não estiver a contar
     */
    public long stop() {
        long now = System.nanoTime();
        if (!running) {
            throw new IllegalStateException("o cronómetro não está a contar");
        }
        running = false;
        elapsed = now - startedAt;
        return elapsed;
    }

    /** @return o tempo da última medição, em nanossegundos */
    public long elapsedNanos() {
        return elapsed;
    }

    /** @return o tempo da última medição, em milissegundos (com casas decimais) */
    public double elapsedMillis() {
        return elapsed / 1_000_000.0;
    }

    /** @return {@code true} entre {@code start()} e {@code stop()} */
    public boolean isRunning() {
        return running;
    }
}